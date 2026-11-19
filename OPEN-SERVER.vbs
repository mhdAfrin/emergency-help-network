Option Explicit
Dim files, shell, target
Set files = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")
target = files.BuildPath(files.GetParentFolderName(WScript.ScriptFullName), "application\scripts\desktop\LaunchDesktop.vbs")
shell.Run "wscript.exe //nologo " & Chr(34) & target & Chr(34) & " server", 0, False
