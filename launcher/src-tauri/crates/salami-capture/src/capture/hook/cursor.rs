//! Draws the mouse pointer into frames that come from the graphics hook.
//!
//! The hook hands over the game's back buffer, and Windows only adds the pointer
//! after that, so without this step menus and inventories are recorded without one.
//! Window capture does not need it: the OS already composes the pointer in there.

use std::ffi::c_void;
use std::mem::size_of;

use windows::Win32::Foundation::{HWND, RECT};
use windows::Win32::Graphics::Direct3D11::{
    ID3D11Texture2D, D3D11_BOX, D3D11_CPU_ACCESS_READ, D3D11_CPU_ACCESS_WRITE,
    D3D11_MAPPED_SUBRESOURCE, D3D11_MAP_READ_WRITE, D3D11_TEXTURE2D_DESC, D3D11_USAGE_STAGING,
};
use windows::Win32::Graphics::Dxgi::Common::{
    DXGI_FORMAT, DXGI_FORMAT_B8G8R8A8_TYPELESS, DXGI_FORMAT_B8G8R8A8_UNORM,
    DXGI_FORMAT_B8G8R8A8_UNORM_SRGB, DXGI_FORMAT_B8G8R8X8_TYPELESS, DXGI_FORMAT_B8G8R8X8_UNORM,
    DXGI_FORMAT_B8G8R8X8_UNORM_SRGB, DXGI_FORMAT_R8G8B8A8_TYPELESS, DXGI_FORMAT_R8G8B8A8_UNORM,
    DXGI_FORMAT_R8G8B8A8_UNORM_SRGB, DXGI_SAMPLE_DESC,
};
use windows::Win32::Graphics::Gdi::{
    DeleteObject, GetDC, GetDIBits, GetObjectW, ReleaseDC, ScreenToClient, BITMAP, BITMAPINFO,
    BITMAPINFOHEADER, BI_RGB, DIB_RGB_COLORS, HBITMAP, HDC, HGDIOBJ,
};
use windows::Win32::UI::HiDpi::{
    SetThreadDpiAwarenessContext, DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2,
};
use windows::Win32::UI::WindowsAndMessaging::{
    CopyIcon, DestroyIcon, GetClientRect, GetCursorInfo, GetForegroundWindow, GetIconInfo,
    CURSORINFO, CURSOR_SHOWING, HCURSOR, HICON, ICONINFO,
};

use crate::capture::{shared, window, CaptureDevice};

const MAX_SIDE: u32 = 1024;
const SCALE_UNIT: u32 = 1000;
const SCALE_TOLERANCE: u32 = 20;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct Texel {
    bgra: [u8; 4],
    invert: bool,
}

const CLEAR: Texel = Texel {
    bgra: [0; 4],
    invert: false,
};

impl Texel {
    fn solid(b: u8, g: u8, r: u8) -> Self {
        Self {
            bgra: [b, g, r, 255],
            invert: false,
        }
    }

    fn inverting(b: u8, g: u8, r: u8) -> Self {
        Self {
            bgra: [b, g, r, 255],
            invert: true,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct Shape {
    width: u32,
    height: u32,
    hot: (i32, i32),
    texels: Vec<Texel>,
}

impl Shape {
    /// A colour pointer: 32-bit pixels plus an AND mask of the same size.
    fn from_color(
        color: &[u8],
        mask: Option<&[u8]>,
        width: u32,
        height: u32,
        hot: (i32, i32),
    ) -> Option<Self> {
        let count = (width * height) as usize;
        if count == 0 || color.len() < count * 4 {
            return None;
        }
        let mask = mask.filter(|mask| mask.len() >= count * 4);
        let has_alpha = color[..count * 4].chunks_exact(4).any(|p| p[3] != 0);

        let texels = (0..count)
            .map(|i| {
                let p = &color[i * 4..i * 4 + 4];
                if has_alpha {
                    return Texel {
                        bgra: [p[0], p[1], p[2], p[3]],
                        invert: false,
                    };
                }
                let masked = mask.is_some_and(|mask| mask[i * 4] != 0);
                match (masked, p[0] | p[1] | p[2] != 0) {
                    (false, _) => Texel::solid(p[0], p[1], p[2]),
                    (true, false) => CLEAR,
                    (true, true) => Texel::inverting(p[0], p[1], p[2]),
                }
            })
            .collect();

        Some(Self {
            width,
            height,
            hot,
            texels,
        })
    }

    /// A monochrome pointer: the AND mask stacked on top of the XOR mask.
    fn from_mask(mask: &[u8], width: u32, height: u32, hot: (i32, i32)) -> Option<Self> {
        let count = (width * height) as usize;
        if count == 0 || mask.len() < count * 8 {
            return None;
        }

        let texels = (0..count)
            .map(|i| {
                let and = mask[i * 4] != 0;
                let xor = mask[(count + i) * 4] != 0;
                match (and, xor) {
                    (false, false) => Texel::solid(0, 0, 0),
                    (false, true) => Texel::solid(255, 255, 255),
                    (true, false) => CLEAR,
                    (true, true) => Texel::inverting(255, 255, 255),
                }
            })
            .collect();

        Some(Self {
            width,
            height,
            hot,
            texels,
        })
    }

    fn scaled(&self, scale: (u32, u32)) -> Self {
        let grow = |value: u32, by: u32| (value * by + SCALE_UNIT / 2) / SCALE_UNIT;
        let width = grow(self.width, scale.0).clamp(1, MAX_SIDE);
        let height = grow(self.height, scale.1).clamp(1, MAX_SIDE);

        let mut texels = Vec::with_capacity((width * height) as usize);
        for y in 0..height {
            let from_y = (y * self.height / height).min(self.height - 1);
            for x in 0..width {
                let from_x = (x * self.width / width).min(self.width - 1);
                texels.push(self.texels[(from_y * self.width + from_x) as usize]);
            }
        }

        Self {
            width,
            height,
            hot: (
                grow(self.hot.0.max(0) as u32, scale.0) as i32,
                grow(self.hot.1.max(0) as u32, scale.1) as i32,
            ),
            texels,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Order {
    Bgra,
    Rgba,
}

impl Order {
    fn of(format: DXGI_FORMAT) -> Option<Self> {
        match format {
            DXGI_FORMAT_B8G8R8A8_UNORM
            | DXGI_FORMAT_B8G8R8A8_UNORM_SRGB
            | DXGI_FORMAT_B8G8R8A8_TYPELESS
            | DXGI_FORMAT_B8G8R8X8_UNORM
            | DXGI_FORMAT_B8G8R8X8_UNORM_SRGB
            | DXGI_FORMAT_B8G8R8X8_TYPELESS => Some(Self::Bgra),
            DXGI_FORMAT_R8G8B8A8_UNORM
            | DXGI_FORMAT_R8G8B8A8_UNORM_SRGB
            | DXGI_FORMAT_R8G8B8A8_TYPELESS => Some(Self::Rgba),
            _ => None,
        }
    }
}

fn blend(dst: &mut [u8], texel: Texel, order: Order) {
    let (blue, red) = match order {
        Order::Bgra => (0, 2),
        Order::Rgba => (2, 0),
    };
    let [b, g, r, alpha] = texel.bgra;

    if texel.invert {
        dst[blue] ^= b;
        dst[1] ^= g;
        dst[red] ^= r;
        return;
    }
    if alpha == 0 {
        return;
    }

    let alpha = alpha as u32;
    let mix = |below: u8, above: u8| {
        ((above as u32 * alpha + below as u32 * (255 - alpha) + 127) / 255) as u8
    };
    dst[blue] = mix(dst[blue], b);
    dst[1] = mix(dst[1], g);
    dst[red] = mix(dst[red], r);
}

/// The part of the pointer that lands inside the frame, in texture pixels.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct Placement {
    x: u32,
    y: u32,
    width: u32,
    height: u32,
    shape_x: u32,
    shape_y: u32,
    flip: bool,
}

impl Placement {
    fn shape_row(&self, row: u32) -> u32 {
        if self.flip {
            self.shape_y + self.height - 1 - row
        } else {
            self.shape_y + row
        }
    }
}

fn place(
    pointer: (i32, i32),
    shape: &Shape,
    target: (u32, u32),
    flip: bool,
) -> Option<Placement> {
    let left = pointer.0 as i64 - shape.hot.0 as i64;
    let top = pointer.1 as i64 - shape.hot.1 as i64;

    let x0 = left.max(0);
    let y0 = top.max(0);
    let x1 = (left + shape.width as i64).min(target.0 as i64);
    let y1 = (top + shape.height as i64).min(target.1 as i64);
    if x1 <= x0 || y1 <= y0 {
        return None;
    }

    Some(Placement {
        x: x0 as u32,
        y: if flip {
            (target.1 as i64 - y1) as u32
        } else {
            y0 as u32
        },
        width: (x1 - x0) as u32,
        height: (y1 - y0) as u32,
        shape_x: (x0 - left) as u32,
        shape_y: (y0 - top) as u32,
        flip,
    })
}

fn scale_between(client: (u32, u32), target: (u32, u32)) -> (u32, u32) {
    let axis = |client: u32, target: u32| {
        let scale = (target as u64 * SCALE_UNIT as u64 / client.max(1) as u64) as u32;
        if scale.abs_diff(SCALE_UNIT) <= SCALE_TOLERANCE {
            SCALE_UNIT
        } else {
            scale
        }
    };
    (axis(client.0, target.0), axis(client.1, target.1))
}

struct Pointer {
    handle: HCURSOR,
    position: (i32, i32),
    client: (u32, u32),
}

struct Cached {
    handle: isize,
    scale: (u32, u32),
    shape: Option<Shape>,
}

struct Scratch {
    texture: ID3D11Texture2D,
    width: u32,
    height: u32,
    format: DXGI_FORMAT,
}

pub struct CursorOverlay {
    hwnd: HWND,
    pid: u32,
    cached: Option<Cached>,
    scratch: Option<Scratch>,
    warned: bool,
}

impl CursorOverlay {
    /// Must be created on the thread that draws: it switches that thread to
    /// physical pixels so the pointer position matches the game's back buffer.
    pub fn new(hwnd: HWND, pid: u32) -> Self {
        unsafe {
            SetThreadDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
        }
        Self {
            hwnd,
            pid,
            cached: None,
            scratch: None,
            warned: false,
        }
    }

    pub fn draw(&mut self, device: &CaptureDevice, target: &ID3D11Texture2D, flip: bool) {
        let Some(pointer) = self.pointer() else {
            return;
        };

        let desc = shared::describe(target);
        let Some(order) = Order::of(desc.Format) else {
            self.warn_once(format!(
                "The pointer is not drawn into clips of this game: its frames use format {}",
                desc.Format.0
            ));
            return;
        };

        let size = (desc.Width, desc.Height);
        let scale = scale_between(pointer.client, size);
        self.remember_shape(pointer.handle, scale);
        let Some(shape) = self.cached.as_ref().and_then(|c| c.shape.as_ref()) else {
            return;
        };

        let position = (
            (pointer.position.0 as i64 * scale.0 as i64 / SCALE_UNIT as i64) as i32,
            (pointer.position.1 as i64 * scale.1 as i64 / SCALE_UNIT as i64) as i32,
        );
        let Some(placement) = place(position, shape, size, flip) else {
            return;
        };

        if let Err(e) = paint(device, target, &mut self.scratch, shape, placement, order, &desc) {
            self.warn_once(format!("The pointer could not be drawn into the clip: {e}"));
        }
    }

    fn pointer(&self) -> Option<Pointer> {
        unsafe {
            let mut info = CURSORINFO {
                cbSize: size_of::<CURSORINFO>() as u32,
                ..Default::default()
            };
            GetCursorInfo(&mut info).ok()?;
            if info.flags.0 & CURSOR_SHOWING.0 == 0 || info.hCursor.is_invalid() {
                return None;
            }

            let foreground = GetForegroundWindow();
            if foreground.0.is_null() || window::pid_of(foreground) != self.pid {
                return None;
            }

            let mut point = info.ptScreenPos;
            if !ScreenToClient(self.hwnd, &mut point).as_bool() {
                return None;
            }
            let mut client = RECT::default();
            GetClientRect(self.hwnd, &mut client).ok()?;
            let width = (client.right - client.left).max(0) as u32;
            let height = (client.bottom - client.top).max(0) as u32;
            if width == 0 || height == 0 {
                return None;
            }

            Some(Pointer {
                handle: info.hCursor,
                position: (point.x, point.y),
                client: (width, height),
            })
        }
    }

    fn remember_shape(&mut self, handle: HCURSOR, scale: (u32, u32)) {
        let key = handle.0 as isize;
        if self
            .cached
            .as_ref()
            .is_some_and(|c| c.handle == key && c.scale == scale)
        {
            return;
        }

        let shape = unsafe { read_shape(handle) }.map(|shape| {
            if scale == (SCALE_UNIT, SCALE_UNIT) {
                shape
            } else {
                shape.scaled(scale)
            }
        });
        if shape.is_none() {
            log::debug!("Could not read the pointer image {key:#x}; leaving it out of the clip");
        }
        self.cached = Some(Cached {
            handle: key,
            scale,
            shape,
        });
    }

    fn warn_once(&mut self, message: String) {
        if !self.warned {
            self.warned = true;
            log::warn!("{message}");
        }
    }
}

fn paint(
    device: &CaptureDevice,
    target: &ID3D11Texture2D,
    scratch: &mut Option<Scratch>,
    shape: &Shape,
    placement: Placement,
    order: Order,
    desc: &D3D11_TEXTURE2D_DESC,
) -> windows::core::Result<()> {
    let fits = scratch.as_ref().is_some_and(|s| {
        s.format == desc.Format && s.width >= placement.width && s.height >= placement.height
    });
    if !fits {
        *scratch = Some(create_scratch(device, shape.width, shape.height, desc.Format)?);
    }
    let Some(scratch) = scratch.as_ref() else {
        return Ok(());
    };

    let region = D3D11_BOX {
        left: placement.x,
        top: placement.y,
        front: 0,
        right: placement.x + placement.width,
        bottom: placement.y + placement.height,
        back: 1,
    };

    unsafe {
        device
            .context
            .CopySubresourceRegion(&scratch.texture, 0, 0, 0, 0, target, 0, Some(&region));

        let mut mapped = D3D11_MAPPED_SUBRESOURCE::default();
        device
            .context
            .Map(&scratch.texture, 0, D3D11_MAP_READ_WRITE, 0, Some(&mut mapped))?;

        if !mapped.pData.is_null() {
            let base = mapped.pData as *mut u8;
            for row in 0..placement.height {
                let pixels = std::slice::from_raw_parts_mut(
                    base.add(row as usize * mapped.RowPitch as usize),
                    placement.width as usize * 4,
                );
                let from = (placement.shape_row(row) * shape.width + placement.shape_x) as usize;
                let texels = &shape.texels[from..from + placement.width as usize];
                for (pixel, texel) in pixels.chunks_exact_mut(4).zip(texels) {
                    blend(pixel, *texel, order);
                }
            }
        }
        device.context.Unmap(&scratch.texture, 0);

        let blended = D3D11_BOX {
            left: 0,
            top: 0,
            front: 0,
            right: placement.width,
            bottom: placement.height,
            back: 1,
        };
        device.context.CopySubresourceRegion(
            target,
            0,
            placement.x,
            placement.y,
            0,
            &scratch.texture,
            0,
            Some(&blended),
        );
    }

    Ok(())
}

fn create_scratch(
    device: &CaptureDevice,
    width: u32,
    height: u32,
    format: DXGI_FORMAT,
) -> windows::core::Result<Scratch> {
    let desc = D3D11_TEXTURE2D_DESC {
        Width: width,
        Height: height,
        MipLevels: 1,
        ArraySize: 1,
        Format: format,
        SampleDesc: DXGI_SAMPLE_DESC {
            Count: 1,
            Quality: 0,
        },
        Usage: D3D11_USAGE_STAGING,
        BindFlags: 0,
        CPUAccessFlags: (D3D11_CPU_ACCESS_READ.0 | D3D11_CPU_ACCESS_WRITE.0) as u32,
        MiscFlags: 0,
    };

    let mut texture = None;
    unsafe { device.device.CreateTexture2D(&desc, None, Some(&mut texture))? };
    let texture = texture.ok_or_else(windows::core::Error::empty)?;

    Ok(Scratch {
        texture,
        width,
        height,
        format,
    })
}

unsafe fn read_shape(cursor: HCURSOR) -> Option<Shape> {
    let icon = CopyIcon(HICON(cursor.0)).ok()?;

    let mut info = ICONINFO::default();
    let shape = if GetIconInfo(icon, &mut info).is_ok() {
        let shape = decode(&info);
        if !info.hbmMask.is_invalid() {
            let _ = DeleteObject(HGDIOBJ(info.hbmMask.0));
        }
        if !info.hbmColor.is_invalid() {
            let _ = DeleteObject(HGDIOBJ(info.hbmColor.0));
        }
        shape
    } else {
        None
    };

    let _ = DestroyIcon(icon);
    shape
}

unsafe fn decode(info: &ICONINFO) -> Option<Shape> {
    let screen = GetDC(HWND::default());
    if screen.is_invalid() {
        return None;
    }
    let hot = (info.xHotspot as i32, info.yHotspot as i32);

    let shape = if info.hbmColor.is_invalid() {
        read_bitmap(screen, info.hbmMask)
            .and_then(|(width, height, mask)| Shape::from_mask(&mask, width, height / 2, hot))
    } else {
        read_bitmap(screen, info.hbmColor).and_then(|(width, height, color)| {
            let mask = read_bitmap(screen, info.hbmMask)
                .filter(|(w, h, _)| (*w, *h) == (width, height))
                .map(|(_, _, mask)| mask);
            Shape::from_color(&color, mask.as_deref(), width, height, hot)
        })
    };

    ReleaseDC(HWND::default(), screen);
    shape
}

unsafe fn read_bitmap(screen: HDC, bitmap: HBITMAP) -> Option<(u32, u32, Vec<u8>)> {
    if bitmap.is_invalid() {
        return None;
    }

    let mut header = BITMAP::default();
    let read = GetObjectW(
        HGDIOBJ(bitmap.0),
        size_of::<BITMAP>() as i32,
        Some(&mut header as *mut BITMAP as *mut c_void),
    );
    if read == 0 || header.bmWidth <= 0 || header.bmHeight <= 0 {
        return None;
    }

    let (width, height) = (header.bmWidth as u32, header.bmHeight as u32);
    if width > MAX_SIDE || height > MAX_SIDE * 2 {
        return None;
    }

    let mut info = BITMAPINFO {
        bmiHeader: BITMAPINFOHEADER {
            biSize: size_of::<BITMAPINFOHEADER>() as u32,
            biWidth: width as i32,
            biHeight: -(height as i32),
            biPlanes: 1,
            biBitCount: 32,
            biCompression: BI_RGB.0,
            ..Default::default()
        },
        ..Default::default()
    };
    let mut pixels = vec![0u8; (width * height * 4) as usize];
    let lines = GetDIBits(
        screen,
        bitmap,
        0,
        height,
        Some(pixels.as_mut_ptr() as *mut c_void),
        &mut info,
        DIB_RGB_COLORS,
    );

    (lines > 0 && lines as u32 == height).then_some((width, height, pixels))
}

#[cfg(test)]
mod tests {
    use super::*;

    const WHITE: [u8; 4] = [255, 255, 255, 0];
    const BLACK: [u8; 4] = [0, 0, 0, 0];

    fn pixels(values: &[[u8; 4]]) -> Vec<u8> {
        values.iter().flatten().copied().collect()
    }

    fn square(side: u32, hot: (i32, i32)) -> Shape {
        Shape {
            width: side,
            height: side,
            hot,
            texels: (0..side * side)
                .map(|i| Texel::solid(i as u8, 0, 0))
                .collect(),
        }
    }

    #[test]
    fn a_monochrome_pointer_decodes_all_four_mask_combinations() {
        let mask = pixels(&[BLACK, BLACK, WHITE, WHITE, BLACK, WHITE, BLACK, WHITE]);
        let shape = Shape::from_mask(&mask, 4, 1, (0, 0)).unwrap();

        assert_eq!(
            shape.texels,
            vec![
                Texel::solid(0, 0, 0),
                Texel::solid(255, 255, 255),
                CLEAR,
                Texel::inverting(255, 255, 255),
            ]
        );
    }

    #[test]
    fn a_colour_pointer_with_alpha_ignores_the_mask() {
        let color = pixels(&[[10, 20, 30, 128], [0, 0, 0, 0]]);
        let mask = pixels(&[WHITE, WHITE]);
        let shape = Shape::from_color(&color, Some(&mask), 2, 1, (0, 0)).unwrap();

        assert_eq!(shape.texels[0].bgra, [10, 20, 30, 128]);
        assert_eq!(shape.texels[1].bgra[3], 0);
        assert!(!shape.texels[0].invert);
    }

    #[test]
    fn a_colour_pointer_without_alpha_takes_its_outline_from_the_mask() {
        let color = pixels(&[[10, 20, 30, 0], [0, 0, 0, 0], [0, 0, 0, 0], [9, 9, 9, 0]]);
        let mask = pixels(&[BLACK, BLACK, WHITE, WHITE]);
        let shape = Shape::from_color(&color, Some(&mask), 4, 1, (0, 0)).unwrap();

        assert_eq!(
            shape.texels,
            vec![
                Texel::solid(10, 20, 30),
                Texel::solid(0, 0, 0),
                CLEAR,
                Texel::inverting(9, 9, 9),
            ]
        );
    }

    #[test]
    fn truncated_bitmaps_are_refused_instead_of_read_out_of_bounds() {
        assert!(Shape::from_mask(&[0; 8], 2, 1, (0, 0)).is_none());
        assert!(Shape::from_color(&[0; 4], None, 2, 1, (0, 0)).is_none());
    }

    #[test]
    fn blending_covers_opaque_transparent_and_half_transparent_pixels() {
        let mut pixel = [100, 100, 100, 255];
        blend(&mut pixel, Texel::solid(1, 2, 3), Order::Bgra);
        assert_eq!(pixel, [1, 2, 3, 255]);

        let mut pixel = [100, 100, 100, 255];
        blend(&mut pixel, CLEAR, Order::Bgra);
        assert_eq!(pixel, [100, 100, 100, 255]);

        let mut pixel = [0, 100, 200, 255];
        let half = Texel {
            bgra: [255, 255, 255, 128],
            invert: false,
        };
        blend(&mut pixel, half, Order::Bgra);
        assert_eq!(pixel, [128, 178, 228, 255]);
    }

    #[test]
    fn blending_respects_the_channel_order_of_the_frame() {
        let mut pixel = [0, 0, 0, 255];
        blend(&mut pixel, Texel::solid(10, 20, 30), Order::Rgba);
        assert_eq!(pixel, [30, 20, 10, 255]);
    }

    #[test]
    fn inverting_pixels_flip_what_is_underneath() {
        let mut pixel = [0, 255, 15, 255];
        blend(&mut pixel, Texel::inverting(255, 255, 255), Order::Bgra);
        assert_eq!(pixel, [255, 0, 240, 255]);
    }

    #[test]
    fn the_hot_spot_lands_on_the_pointer_position() {
        let placement = place((100, 50), &square(32, (4, 6)), (1920, 1080), false).unwrap();

        assert_eq!((placement.x, placement.y), (96, 44));
        assert_eq!((placement.width, placement.height), (32, 32));
        assert_eq!((placement.shape_x, placement.shape_y), (0, 0));
    }

    #[test]
    fn a_pointer_at_the_edge_is_clipped_to_the_frame() {
        let shape = square(32, (0, 0));

        let corner = place((-10, -20), &shape, (1920, 1080), false).unwrap();
        assert_eq!((corner.x, corner.y), (0, 0));
        assert_eq!((corner.width, corner.height), (22, 12));
        assert_eq!((corner.shape_x, corner.shape_y), (10, 20));

        let far = place((1910, 1070), &shape, (1920, 1080), false).unwrap();
        assert_eq!((far.width, far.height), (10, 10));

        assert!(place((1920, 500), &shape, (1920, 1080), false).is_none());
        assert!(place((500, -32), &shape, (1920, 1080), false).is_none());
    }

    #[test]
    fn a_bottom_up_frame_gets_the_pointer_mirrored_into_place() {
        let shape = square(32, (0, 0));
        let placement = place((100, 10), &shape, (1920, 1080), true).unwrap();

        assert_eq!(placement.y, 1080 - 10 - 32);
        assert_eq!(placement.shape_row(0), 31);
        assert_eq!(placement.shape_row(31), 0);

        let clipped = place((100, 1070), &shape, (1920, 1080), true).unwrap();
        assert_eq!((clipped.y, clipped.height), (0, 10));
        assert_eq!(clipped.shape_row(0), 9);
        assert_eq!(clipped.shape_row(9), 0);
    }

    fn grey_frame(device: &CaptureDevice, side: u32) -> ID3D11Texture2D {
        use windows::Win32::Graphics::Direct3D11::{
            D3D11_BIND_RENDER_TARGET, D3D11_BIND_SHADER_RESOURCE, D3D11_SUBRESOURCE_DATA,
            D3D11_USAGE_DEFAULT,
        };

        let grey = vec![128u8; (side * side * 4) as usize];
        let desc = D3D11_TEXTURE2D_DESC {
            Width: side,
            Height: side,
            MipLevels: 1,
            ArraySize: 1,
            Format: DXGI_FORMAT_B8G8R8A8_UNORM,
            SampleDesc: DXGI_SAMPLE_DESC {
                Count: 1,
                Quality: 0,
            },
            Usage: D3D11_USAGE_DEFAULT,
            BindFlags: (D3D11_BIND_RENDER_TARGET.0 | D3D11_BIND_SHADER_RESOURCE.0) as u32,
            CPUAccessFlags: 0,
            MiscFlags: 0,
        };
        let data = D3D11_SUBRESOURCE_DATA {
            pSysMem: grey.as_ptr() as *const c_void,
            SysMemPitch: side * 4,
            SysMemSlicePitch: 0,
        };

        let mut texture = None;
        unsafe {
            device
                .device
                .CreateTexture2D(&desc, Some(&data), Some(&mut texture))
                .unwrap();
        }
        texture.unwrap()
    }

    fn read_back(device: &CaptureDevice, texture: &ID3D11Texture2D, side: u32) -> Vec<u8> {
        let copy = create_scratch(device, side, side, DXGI_FORMAT_B8G8R8A8_UNORM).unwrap();
        let mut pixels = Vec::with_capacity((side * side * 4) as usize);
        unsafe {
            device.context.CopyResource(&copy.texture, texture);
            let mut mapped = D3D11_MAPPED_SUBRESOURCE::default();
            device
                .context
                .Map(&copy.texture, 0, D3D11_MAP_READ_WRITE, 0, Some(&mut mapped))
                .unwrap();
            for row in 0..side as usize {
                pixels.extend_from_slice(std::slice::from_raw_parts(
                    (mapped.pData as *const u8).add(row * mapped.RowPitch as usize),
                    side as usize * 4,
                ));
            }
            device.context.Unmap(&copy.texture, 0);
        }
        pixels
    }

    #[test]
    fn the_system_arrow_is_drawn_into_a_frame_on_the_gpu() {
        use windows::Win32::Foundation::HINSTANCE;
        use windows::Win32::UI::WindowsAndMessaging::{LoadCursorW, IDC_ARROW};

        let Ok(device) = CaptureDevice::new_default() else {
            return;
        };
        const SIDE: u32 = 128;

        let arrow = unsafe { LoadCursorW(HINSTANCE::default(), IDC_ARROW) }.unwrap();
        let shape = unsafe { read_shape(arrow) }.expect("the system arrow has an image");
        assert!(shape.texels.iter().any(|t| t.invert || t.bgra[3] != 0));

        let frame = grey_frame(&device, SIDE);
        let desc = shared::describe(&frame);
        let mut scratch = None;
        for (pointer, flip) in [((8, 8), false), ((70, 8), true), ((120, 120), false)] {
            let placement = place(pointer, &shape, (SIDE, SIDE), flip).unwrap();
            paint(&device, &frame, &mut scratch, &shape, placement, Order::Bgra, &desc).unwrap();
        }

        let pixels = read_back(&device, &frame, SIDE);
        let changed = pixels.chunks_exact(4).filter(|p| p[..3] != [128, 128, 128]).count();
        assert!(changed > 0, "painting left the frame untouched");
        assert!(changed < (SIDE * SIDE / 2) as usize, "painting spilled over the frame");

        let untouched = (40 * SIDE + 50) as usize * 4;
        assert_eq!(pixels[untouched..untouched + 3], [128, 128, 128]);
    }

    #[test]
    fn a_frame_the_size_of_the_window_needs_no_scaling() {
        assert_eq!(scale_between((2560, 1369), (2560, 1369)), (1000, 1000));
        assert_eq!(scale_between((2560, 1440), (2561, 1440)), (1000, 1000));
        assert_eq!(scale_between((2560, 1440), (1280, 720)), (500, 500));
        assert_eq!(scale_between((0, 0), (1280, 720)).0, 1_280_000);
    }

    #[test]
    fn scaling_keeps_the_hot_spot_in_proportion() {
        let scaled = square(32, (8, 16)).scaled((500, 1500));

        assert_eq!((scaled.width, scaled.height), (16, 48));
        assert_eq!(scaled.hot, (4, 24));
        assert_eq!(scaled.texels.len(), 16 * 48);
        assert_eq!(scaled.texels[0], square(32, (0, 0)).texels[0]);
    }
}
