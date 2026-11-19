Option Explicit
Dim shell, files, scriptFolder, command, mode, scriptPath
Set shell = CreateObject("WScript.Shell")
Set files = CreateObject("Scripting.FileSystemObject")
scriptFolder = files.GetParentFolderName(WScript.ScriptFullName)
scriptPath = files.BuildPath(scriptFolder, "LaunchDesktop.ps1")
mode = "client"
If WScript.Arguments.Count > 0 Then mode = WScript.Arguments(0)
If mode <> "client" And mode <> "server" Then
  MsgBox "Choose a server or client launcher.", vbExclamation, "Emergency Help Network"
  WScript.Quit 1
End If
command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File " & Chr(34) & scriptPath & Chr(34) & " -Mode " & mode
shell.Run command, 0, False
