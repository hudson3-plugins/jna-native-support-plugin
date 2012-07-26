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

import com.sun.akuma.Daemon;
import com.sun.akuma.JavaVMArguments;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.StringArray;
import hudson.Extension;
import java.io.File;
import java.util.Map;
import java.util.Set;
import org.eclipse.hudson.jna.*;
import static org.hudsonci.plugins.jna.GNUCLibrary.*;
import org.jruby.ext.posix.FileStat;
import org.jruby.ext.posix.Group;
import org.jruby.ext.posix.POSIX;
import org.jruby.ext.posix.Passwd;
import org.jvnet.hudson.MemoryMonitor;
import org.jvnet.hudson.MemoryUsage;
import org.jvnet.libpam.PAM;
import org.jvnet.libpam.UnixUser;
import org.jvnet.libpam.impl.CLibrary;
import org.kohsuke.stapler.DataBoundConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * JNA based Native Support Extension for Hudson
 */
public class JnaNativeUnixSupport extends NativeUnixSupport {

    private transient Logger logger = LoggerFactory.getLogger(JnaNativeUnixSupport.class);

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
            case PAM:
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

            try {
                return PosixAPI.get().chmod(file.getAbsolutePath(), mask) == 0;
            } catch (Throwable ex) {
                throw new NativeAccessException("Failed to do chmod. " + ex.getLocalizedMessage());
            }  
        }
    }

    @Override
    public boolean chown(File file, int uid, int gid) {
        try {
            return LIBC.chown(file.getPath(), uid, gid) == 0;
        } catch (Throwable ex) {
            throw new NativeAccessException("Failed to do chown. " + ex.getLocalizedMessage());
        }
    }

    @Override
    public int mode(File file) {
        try {
            return PosixAPI.get().stat(file.getPath()).mode();
        } catch (Throwable ex) {
            throw new NativeAccessException("Failed to get File mode. " + ex.getLocalizedMessage());
        }  
    }

    @Override
    public boolean makeFileWritable(File file) {
        try {
            POSIX posix = PosixAPI.get();
            String path = file.getAbsolutePath();
            FileStat stat = posix.stat(path);
            return posix.chmod(path, stat.mode() | 0200) == 0; // u+w
        } catch (Throwable ex) {
            throw new NativeAccessException("Failed to make file writable. " + ex.getLocalizedMessage());
        } 
    }

    @Override
    public boolean createSymlink(String targetPath, File symlinkFile) throws NativeAccessException {
        return createSymlink(targetPath, symlinkFile, false);
    }

    public boolean createSymlink(String targetPath, File symlinkFile, boolean usePosix) throws NativeAccessException {
        if (usePosix) {
            return PosixAPI.get().symlink(symlinkFile.getAbsolutePath(), targetPath) == 0;

        } else {
            try {
                return LIBC.symlink(symlinkFile.getAbsolutePath(), targetPath) == 0;
            } catch (LinkageError exc) {
                logger.info("Could not create symlink with JNA. From - " + symlinkFile
                        + " to " + targetPath + ". " + exc.getLocalizedMessage()
                        + " Trying Posix API..");
                // if JNA is unavailable, fall back.
                // we still prefer to try JNA first as PosixAPI supports even smaller platforms.
                try {
                    return PosixAPI.get().symlink(symlinkFile.getAbsolutePath(), targetPath) == 0;
                } catch (Throwable ex) {
                    throw new NativeAccessException("Failed to Create Symlink. " + ex.getLocalizedMessage());
                } 
            }
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
                    if (err == 22/*
                             * EINVAL --- but is this really portable?
                             */) {
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
            } catch (Throwable ex) {
                throw new NativeAccessException("Failed to Resolve Symlink. " + ex.getLocalizedMessage());
            }  
        }

    }

    @Override
    public NativeSystemMemory getSystemMemory() throws NativeAccessException {
        try {
            return new SystemMemoryImpl(MemoryMonitor.get().monitor());
        } catch (Throwable exc) {
            throw new NativeAccessException("Failed to get System Memory. " + exc.getLocalizedMessage());
        }  
    }

    @Override
    public int getEuid() throws NativeAccessException {
        try {
            return LIBC.geteuid();
        } catch (Throwable exc) {
            throw new NativeAccessException("Failed to get Euid. " + exc.getLocalizedMessage());
        } 
    }

    @Override
    public int getEgid() throws NativeAccessException {
        try {
            return LIBC.getegid();
        } catch (Throwable exc) {
            throw new NativeAccessException("Failed to get Egid. " + exc.getLocalizedMessage());
        } 
    }

    @Override
    public String getProcessUser() throws NativeAccessException {
        try {
            return LIBC.getpwuid(getEuid()).pw_name;
        } catch (Throwable exc) {
            throw new NativeAccessException("Failed to get Process User. " + exc.getLocalizedMessage());
        }  
    }

    @Override
    public void restartJavaProcess(Map<String, String> properties, boolean daemonExec) throws NativeAccessException {
        JavaVMArguments args;
        try {
            args = JavaVMArguments.current();

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
        } catch (Throwable ex) {
            throw new NativeAccessException("Failed to restart Java Process. " + ex.getLocalizedMessage());
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
        } catch (Throwable exc) {
            logger.info("Failed to find Java process arguments", exc.getLocalizedMessage());
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
        try {
            return CLibrary.libc.getgrnam(groupName) != null;
        } catch (Throwable exc) {
            throw new NativeAccessException("Failed to get Unix Group. " + exc.getLocalizedMessage());
        } 
    }

    @Override
    public Set<String> pamAuthenticate(String serviceName, String userName, String password) throws NativeAccessException {
        if (serviceName == null) {
            serviceName = "sshd"; // use sshd as the default
        }
        try {
            UnixUser unixUser = new PAM(serviceName).authenticate(userName, password);
            return unixUser.getGroups();
        } catch (Throwable exc) {
            throw new NativeAccessException("Failed to do Pam Authentication. " + exc.getLocalizedMessage());
        }  

    }

    @Override
    public String checkPamAuthentication() throws NativeAccessException {
        try {
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
        } catch (Throwable exc) {
            throw new NativeAccessException("Failed to check Pam Authentication. " + exc.getLocalizedMessage());
        } 
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
