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
package org.hudsonci.plugins.jna;

import org.jruby.ext.posix.Group;
import org.jruby.ext.posix.Passwd;
import org.jvnet.libpam.PAM;
import java.util.Set;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.impl.CLibrary;
import org.jvnet.libpam.UnixUser;
import com.sun.akuma.JavaVMArguments;
import com.sun.akuma.Daemon;
import com.sun.jna.StringArray;
import java.util.logging.Level;
import com.sun.jna.Memory;
import hudson.Extension;
import java.io.File;
import java.util.Map;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;

import static org.hudsonci.plugins.jna.GNUCLibrary.LIBC;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import hudson.util.jna.NativeAccessException;
import hudson.util.jna.NativeFunction;
import hudson.util.jna.NativeSystemMemory;
import hudson.util.jna.NativeUnixSupport;
import hudson.util.jna.NativeUnixSupportDescriptor;
import java.io.IOException;
import org.jruby.ext.posix.FileStat;
import org.jruby.ext.posix.POSIX;
import org.jvnet.hudson.MemoryMonitor;
import org.jvnet.hudson.MemoryUsage;


import static org.hudsonci.plugins.jna.GNUCLibrary.FD_CLOEXEC;
import static org.hudsonci.plugins.jna.GNUCLibrary.F_GETFD;
import static org.hudsonci.plugins.jna.GNUCLibrary.F_SETFD;

/**
 *
 *  JNA based Native Support Extension for Hudson
 */
public class JnaNativeUnixSupport extends NativeUnixSupport {

    private static final Logger LOGGER = Logger.getLogger(JnaNativeUnixSupport.class.getName());

    @DataBoundConstructor
    public JnaNativeUnixSupport() {
    }

    @Override
    public boolean hasSupportFor(NativeFunction nativeFunc) {
        switch (nativeFunc) {
            case CHMOD:
                return true;
            case CHOWN:
                return true;
            case MODE:
                return true;
            case FILE_WRITABLE:
                return true;
            case SYMLINK:
                return true;
            case RESOLVE_LINK:
                return true;
            case DOTNET:
                return true;
            case SYSTEM_MEMORY:
                return true;
            case EUID:
                return true;
            case EGID:
                return true;
            case JAVA_RESTART:
                return true;
            case UNIX_USER:
                return true;
            case UNIX_GROUP:
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
    public boolean chmod(File file, int mask) {
        try {
            return LIBC.chmod(file.getAbsolutePath(), mask) == 0;
        } catch (LinkageError e) {
            // if JNA is unavailable, fall back.
            // we still prefer to try JNA first as PosixAPI supports even smaller platforms.
            return PosixAPI.get().chmod(file.getAbsolutePath(), mask) == 0;
        }
    }

    @Override
    public boolean chown(File file, int uid, int gid) {
        return LIBC.chown(file.getPath(), uid, gid) == 0;
    }

    @Override
    public int mode(File file) {
        return PosixAPI.get().stat(file.getPath()).mode();
    }

    @Override
    public boolean makeFileWritable(File file) {
        POSIX posix = PosixAPI.get();
        String path = file.getAbsolutePath();
        FileStat stat = posix.stat(path);
        return posix.chmod(path, stat.mode() | 0200) == 0; // u+w
    }

    @Override
    public boolean createSymlink(String targetPath, File symlinkFile) {
        try {
            return LIBC.symlink(symlinkFile.getAbsolutePath(), targetPath) == 0;
        } catch (LinkageError e) {
            // if JNA is unavailable, fall back.
            // we still prefer to try JNA first as PosixAPI supports even smaller platforms.
            return PosixAPI.get().symlink(symlinkFile.getAbsolutePath(), targetPath) == 0;
        }
    }

    @Override
    public String resolveSymlink(File linkFile) throws NativeAccessException {
        String filename = linkFile.getAbsolutePath();
        try {
            for (int sz = 512; sz < 65536; sz *= 2) {
                Memory m = new Memory(sz);
                int r = LIBC.readlink(filename, m, new NativeLong(sz));
                if (r < 0) {
                    int err = Native.getLastError();
                    if (err == 22/*EINVAL --- but is this really portable?*/) {
                        return null; // this means it's not a symlink
                    }
                    throw new NativeAccessException("Failed to readlink " + linkFile + " error=" + err + " " + LIBC.strerror(err));
                }
                if (r == sz) {
                    continue;   // buffer too small
                }
                byte[] buf = new byte[r];
                m.read(0, buf, 0, r);
                return new String(buf);
            }
            // something is wrong. It can't be this long!
            throw new NativeAccessException("Symlink too long: " + linkFile);
        } catch (LinkageError e) {
            try {
                // if JNA is unavailable, fall back.
                // we still prefer to try JNA first as PosixAPI supports even smaller platforms.
                return PosixAPI.get().readlink(filename);
            } catch (IOException ex) {
                throw new NativeAccessException(ex.getLocalizedMessage());
            }
        }

    }

    @Override
    public NativeSystemMemory getSystemMemory() throws NativeAccessException {
        try {
            return new SystemMemoryImpl(MemoryMonitor.get().monitor());
        } catch (IOException exc) {
            throw new NativeAccessException(exc);
        }
    }

    @Override
    public int getEuid() throws NativeAccessException {
        return LIBC.geteuid();
    }

    @Override
    public int getEgid() throws NativeAccessException {
        return LIBC.getegid();
    }

    @Override
    public String getProcessUser() throws NativeAccessException {
        return LIBC.getpwuid(getEuid()).pw_name;
    }

    @Override
    public void restartJavaProcess(Map<String, String> properties, boolean daemonExec) throws NativeAccessException {
        JavaVMArguments args;
        try {
            args = JavaVMArguments.current();
        } catch (IOException ex) {
            throw new NativeAccessException("Failed to restart Java Process. " + LIBC.strerror(Native.getLastError()));
        }
        // close all files upon exec, except stdin, stdout, and stderr
        int sz = LIBC.getdtablesize();
        for (int i = 3; i < sz; i++) {
            int flags = LIBC.fcntl(i, F_GETFD);
            if (flags < 0) {
                continue;
            }
            LIBC.fcntl(i, F_SETFD, flags | FD_CLOEXEC);
        }

        if (properties != null) {
            for (String key : properties.keySet()) {
                args.setSystemProperty(key, properties.get(key));
            }
        }

        if (daemonExec) {
            Daemon.selfExec(args);
        } else {
            // Execute the Java process
            LIBC.execv(
                    Daemon.getCurrentExecutable(),
                    new StringArray(args.toArray(new String[args.size()])));
        }
        throw new NativeAccessException("Failed to restart Java Process. " + LIBC.strerror(Native.getLastError()));
    }

    @Override
    public boolean canRestartJavaProcess() throws NativeAccessException {
        JavaVMArguments args;
        try {
            args = JavaVMArguments.current();
            if (args != null) {
                return true;
            }
        } catch (IOException exc) {
            LOGGER.log(Level.FINE, "Failed to find Java process arguments", exc);
            // Fall through. Failed to find the Java process arguments
            // So not possible to start it anyway.
        }
        return false;
    }

    @Override
    public boolean checkUnixUser(String userName) throws NativeAccessException {
        return UnixUser.exists(userName);
    }

    @Override
    public boolean checkUnixGroup(String groupName) throws NativeAccessException {
        return CLibrary.libc.getgrnam(groupName) != null;
    }

    @Override
    public Set<String> pamAuthenticate(String serviceName, String userName, String password) throws NativeAccessException {
        if (serviceName == null) {
            serviceName = "sshd"; // use sshd as the default
        }
        try {
            UnixUser unixUser = new PAM(serviceName).authenticate(userName, password);
            return unixUser.getGroups();
        } catch (PAMException ex) {
            throw new NativeAccessException(ex);
        }

    }

    @Override
    public String checkPamAuthentication() throws NativeAccessException {
        File s = new File("/etc/shadow");
        if (s.exists() && !s.canRead()) {
            // it looks like shadow password is in use, but we don't have read access
            System.out.println("Shadow in use");
            POSIX api = PosixAPI.get();
            FileStat st = api.stat("/etc/shadow");
            if (st == null) {
                return "Error:" + Messages.PAMSecurityRealm_ReadPermission();
            }

            Passwd pwd = api.getpwuid(api.geteuid());
            String user;
            if (pwd != null) {
                user = "Error:" + Messages.PAMSecurityRealm_User(pwd.getLoginName());
            } else {
                user = "Error:" + Messages.PAMSecurityRealm_CurrentUser();
            }

            String group;
            Group g = api.getgrgid(st.gid());
            if (g != null) {
                group = g.getName();
            } else {
                group = String.valueOf(st.gid());
            }

            if ((st.mode() & FileStat.S_IRGRP) != 0) {
                // the file is readable to group. Hudson should be in the right group, then
                return "Error:" + Messages.PAMSecurityRealm_BelongToGroup(user, group);
            } else {
                Passwd opwd = api.getpwuid(st.uid());
                String owner;
                if (opwd != null) {
                    owner = opwd.getLoginName();
                } else {
                    owner = "Error:" + Messages.PAMSecurityRealm_Uid(st.uid());
                }

                return "Error:" + Messages.PAMSecurityRealm_RunAsUserOrBelongToGroupAndChmod(owner, user, group);
            }
        }
        return Messages.PAMSecurityRealm_Success();
    }

    private static class SystemMemoryImpl implements NativeSystemMemory {

        MemoryUsage memoryUsage;

        SystemMemoryImpl(MemoryUsage mem) {
            memoryUsage = mem;
        }

        public long getAvailablePhysicalMemory() {
            return memoryUsage.availablePhysicalMemory;
        }

        public long getAvailableSwapSpace() {
            return memoryUsage.availableSwapSpace;
        }

        public long getTotalPhysicalMemory() {
            return memoryUsage.totalPhysicalMemory;
        }

        public long getTotalSwapSpace() {
            return memoryUsage.totalSwapSpace;
        }
    }

    @Extension
    public static class DescriptorImpl extends NativeUnixSupportDescriptor {

        @Override
        public String getDisplayName() {
            return "JNA based Native Unix Support";
        }
    }
}
