' ============================================
' XiYouJi Game Launcher - Silent Start
' Double-click to start the game without console window
' ============================================
Set WshShell = CreateObject("WScript.Shell")
Set FSO = CreateObject("Scripting.FileSystemObject")

strDir = FSO.GetParentFolderName(WScript.ScriptFullName)
strBat = strDir & "\start_game.bat"

' Use English filename to avoid encoding issues
If Not FSO.FileExists(strBat) Then
    MsgBox "Cannot find: " & strBat, 16, "Error"
    WScript.Quit(1)
End If

' Run bat hidden (window style 0)
WshShell.Run """" & strBat & """", 0, False

MsgBox "Game is starting..." & vbCrLf & vbCrLf & _
       "Browser will open automatically in ~10 seconds." & vbCrLf & _
       "If not, visit: http://localhost:8080" & vbCrLf & vbCrLf & _
       "To stop: run stop_game.bat or kill java.exe in Task Manager.", _
       64, "XiYouJi Roguelike"
