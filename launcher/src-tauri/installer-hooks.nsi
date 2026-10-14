; Salami Client - NSIS Installer Hooks
; Entfernt die Empfehlungs-Datei alter NoRisk-Installationen; Salami wertet keine
; Empfehlungscodes aus dem Installer-Dateinamen aus.

!macro NSIS_HOOK_POSTINSTALL
  Delete "$INSTDIR\referral_code.txt"
!macroend

!macro NSIS_HOOK_PREINSTALL
  ; No action needed before install
!macroend

!macro NSIS_HOOK_POSTUNINSTALL
  Delete "$INSTDIR\referral_code.txt"
!macroend

!macro NSIS_HOOK_PREUNINSTALL
  ; No action needed before uninstall
!macroend
