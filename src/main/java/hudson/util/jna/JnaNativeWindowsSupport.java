/*
 * The MIT License
 * 
 * Copyright 2011 Winston.Prakash@Oracle.com
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.util.jna;

import hudson.Extension;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;

import static hudson.util.jna.GNUCLibrary.LIBC;
import com.sun.jna.Native;
import java.util.ArrayList;
import org.jvnet.winp.WinProcess;

import static hudson.util.jna.Kernel32.MOVEFILE_DELAY_UNTIL_REBOOT;
import static hudson.util.jna.Kernel32.MOVEFILE_REPLACE_EXISTING;

/**
 *
 *  JNA based Native Support Extension for Hudson
 */
public class JnaNativeWindowsSupport extends NativeWindowsSupport {

    private static final Logger LOGGER = Logger.getLogger(JnaNativeWindowsSupport.class.getName());

    @DataBoundConstructor
    public JnaNativeWindowsSupport() {
    }

    @Override
    public boolean hasSupportFor(NativeFunction nativeFunc) {
        switch (nativeFunc) {
            case WINDOWS_PROCESS:
                return true;
            case WINDOWS_EXEC:
                return true;
            case WINDOWS_FILE_MOVE:
                return true;
            case ERROR:
                return true;
        }
        return false;
    }

    @Override
    public String getLastError() {
        return LIBC.strerror(Native.getLastError());
    }


    @Override
    public boolean isDotNetInstalled(int major, int minor) throws NativeAccessException {
        return DotNet.isInstalled(major, minor);
    }


    @Override
    public List<NativeProcess> getWindowsProcesses() {
        WinProcess.enableDebugPrivilege();
        List<NativeProcess> processList = new ArrayList<NativeProcess>();
        for (final WinProcess process : WinProcess.all()) {
            processList.add(new NativeWindowsProcess(process));
        }

        return processList;
    }

    @Override
    public int getWindowsProcessId(Process process) {
        return new WinProcess(process).getPid();
    }

    @Override
    public int windowsExec(File winExe, String args, String logFile, File pwd) throws NativeAccessException {
        // error code 740 is ERROR_ELEVATION_REQUIRED, indicating that
        // we run in UAC-enabled Windows and we need to run this in an elevated privilege
        SHELLEXECUTEINFO sei = new SHELLEXECUTEINFO();
        sei.fMask = SHELLEXECUTEINFO.SEE_MASK_NOCLOSEPROCESS;
        sei.lpVerb = "runas";
        sei.lpFile = winExe.getAbsolutePath();
        sei.lpParameters = "/redirect " + logFile + " " + args;
        sei.lpDirectory = pwd.getAbsolutePath();
        sei.nShow = SHELLEXECUTEINFO.SW_HIDE;
        if (!Shell32.INSTANCE.ShellExecuteEx(sei)) {
            throw new NativeAccessException("Failed to shellExecute: " + Native.getLastError());
        }
        try {
            return Kernel32Utils.waitForExitProcess(sei.hProcess);
        } catch (InterruptedException ex) {
            throw new NativeAccessException("Failed to shellExecute: " + Native.getLastError());
        }
    }

    @Override
    public void windowsMoveFile(File fromFile, File toFile) {
        Kernel32.INSTANCE.MoveFileExA(fromFile.getAbsolutePath(), toFile.getAbsolutePath(), MOVEFILE_DELAY_UNTIL_REBOOT | MOVEFILE_REPLACE_EXISTING);
    }
     
    private static class NativeWindowsProcess implements NativeProcess {

        WinProcess nativeWindowsProcess;

        NativeWindowsProcess(WinProcess process) {
            nativeWindowsProcess = process;
        }

        public int getPid() {
            return nativeWindowsProcess.getPid();
        }

        public int getPpid() {
            // Information not available
            return -1;
        }

        public void killRecursively() {
            nativeWindowsProcess.killRecursively();
        }

        public void kill() {
            nativeWindowsProcess.kill();
        }

        public void setPriority(int priority) {
            nativeWindowsProcess.setPriority(priority);
        }

        public String getCommandLine() {
            return nativeWindowsProcess.getCommandLine();
        }

        public Map<String, String> getEnvironmentVariables() {
            return nativeWindowsProcess.getEnvironmentVariables();
        }
    } 

    @Extension
    public static class DescriptorImpl extends NativeWindowsSupportDescriptor {

        @Override
        public String getDisplayName() {
            return "JNA based Native Windows Support";
        }
    }
}
