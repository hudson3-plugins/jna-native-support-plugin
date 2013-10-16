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

import java.util.HashMap;
import java.io.ByteArrayOutputStream;
import com.sun.jna.ptr.IntByReference;
import java.util.logging.Level;
import com.sun.jna.Memory;
import hudson.Extension;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;

import static org.hudsonci.plugins.jna.GNUCLibrary.LIBC;
import com.sun.jna.Native;
import java.io.IOException;
import java.util.ArrayList;

import static com.sun.jna.Pointer.NULL;
import org.eclipse.hudson.jna.*;

/**
 *
 * JNA based Native Support Extension for Hudson
 */
public class JnaNativeMacSupport extends NativeMacSupport {

    private static final Logger LOGGER = Logger.getLogger(JnaNativeMacSupport.class.getName());
    
    // local constants
    private int sizeOf_kinfo_proc;
    private final int sizeOf_kinfo_proc_32 = 492; // on 32bit Mac OS X.
    private final int sizeOf_kinfo_proc_64 = 648; // on 64bit Mac OS X.
    private int kinfo_proc_pid_offset;
    private int kinfo_proc_pid_offset_32 = 24;
    private int kinfo_proc_pid_offset_64 = 40;
    private int kinfo_proc_ppid_offset;
    private int kinfo_proc_ppid_offset_32 = 416;
    private int kinfo_proc_ppid_offset_64 = 560;
    private final int CTL_KERN = 1;
    private final int KERN_PROC = 14;
    private final int KERN_PROC_ALL = 0;
    private final int ENOMEM = 12;
    private int[] MIB_PROC_ALL = {CTL_KERN, KERN_PROC, KERN_PROC_ALL};
    private final int KERN_ARGMAX = 8;
    private final int KERN_PROCARGS2 = 49;

    @DataBoundConstructor
    public JnaNativeMacSupport() {
    }

    @Override
    public boolean hasSupportFor(NativeFunction nativeFunc) {
        switch (nativeFunc) {
            case MAC_PROCESS:
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
    public List<NativeProcess> getMacProcesses() throws NativeAccessException {
        int sizeOfInt = Native.getNativeSize(int.class);
        List<NativeProcess> processList = new ArrayList<NativeProcess>();

        String arch = System.getProperty("sun.arch.data.model");
        if ("64".equals(arch)) {
            sizeOf_kinfo_proc = sizeOf_kinfo_proc_64;
            kinfo_proc_pid_offset = kinfo_proc_pid_offset_64;
            kinfo_proc_ppid_offset = kinfo_proc_ppid_offset_64;
        } else {
            sizeOf_kinfo_proc = sizeOf_kinfo_proc_32;
            kinfo_proc_pid_offset = kinfo_proc_pid_offset_32;
            kinfo_proc_ppid_offset = kinfo_proc_ppid_offset_32;
        }

        try {
            IntByReference _ = new IntByReference(sizeOfInt);
            IntByReference size = new IntByReference(sizeOfInt);
            Memory m;
            int nRetry = 0;
            while (true) {
                // find out how much memory we need to do this
                if (LIBC.sysctl(MIB_PROC_ALL, 3, NULL, size, NULL, _) != 0) {
                    throw new IOException("Failed to obtain memory requirement: " + LIBC.strerror(Native.getLastError()));
                }

                // now try the real call
                m = new Memory(size.getValue());
                if (LIBC.sysctl(MIB_PROC_ALL, 3, m, size, NULL, _) != 0) {
                    if (Native.getLastError() == ENOMEM && nRetry++ < 16) {
                        continue; // retry
                    }
                    throw new IOException("Failed to call kern.proc.all: " + LIBC.strerror(Native.getLastError()));
                }
                break;
            }

            int count = size.getValue() / sizeOf_kinfo_proc;
            LOGGER.log(Level.FINE, "Found {0} processes", count);

            for (int base = 0; base < size.getValue(); base += sizeOf_kinfo_proc) {
                int pid = m.getInt(base + kinfo_proc_pid_offset);
                int ppid = m.getInt(base + kinfo_proc_ppid_offset);
//              int effective_uid = m.getInt(base+304);
//              byte[] comm = new byte[16];
//              m.read(base+163,comm,0,16);

                processList.add(new NativeMacProcess(pid, ppid));
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to obtain process list", e);
        }
        return processList;
    }

    private class NativeMacProcess implements NativeProcess {

        private final int pid;
        private final int ppid;
        private Map<String, String> envVars;
        private List<String> arguments;

        NativeMacProcess(int pid, int ppid) {
            this.pid = pid;
            this.ppid = ppid;
        }

        public int getPid() {
            return pid;
        }

        public int getPpid() {
            return ppid;
        }

        public void killRecursively() {
            throw new UnsupportedOperationException("This native opration is not yet supported on Mac");
        }

        public void kill() {
            throw new UnsupportedOperationException("This native Opration is not yet supported on Mac");
        }

        public void setPriority(int priority) {
            throw new UnsupportedOperationException("This native Opration is not yet supported on Mac");
        }

        public synchronized String getCommandLine() {
            if (arguments == null) {
                parse();
            }
            StringBuilder commandLine = new StringBuilder();
            for (String arg : arguments) {
                commandLine.append(arg);
                commandLine.append(" ");
            }
            return commandLine.toString();
        }

        public synchronized Map<String, String> getEnvironmentVariables() {
            if (envVars == null) {
                parse();
            }
            return envVars;
        }

        private void parse() {
            final int sizeOfInt = Native.getNativeSize(int.class);
            try {
                // allocate them first, so that the parse error wil result in empty data
                // and avoid retry.
                arguments = new ArrayList<String>();
                envVars = new HashMap<String, String>();

                class StringArrayMemory extends Memory {

                    private long offset = 0;

                    StringArrayMemory(long l) {
                        super(l);
                    }

                    int readInt() {
                        int r = getInt(offset);
                        offset += sizeOfInt;
                        return r;
                    }

                    boolean hasMore() {
                        return offset < getSize();
                    }

                    byte peek() {
                        return getByte(offset);
                    }

                    String readString() {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte ch;
                        while (hasMore() && (ch = getByte(offset++)) != '\0') {
                            baos.write(ch);
                        }
                        return baos.toString();
                    }

                    void skip0() {
                        // skip trailing '\0's
                        while (getByte(offset) == '\0') {
                            offset++;
                        }
                    }
                }
                IntByReference newSize = new IntByReference();
                IntByReference size = new IntByReference(sizeOfInt);

                // First determine the size of the memory blob we need to allocate
                if (LIBC.sysctl(new int[]{CTL_KERN, KERN_PROCARGS2, pid}, 3, NULL, size, NULL, newSize) != 0) {
                    throw new IOException("Failed to obtain size for kern.procargs2: " + LIBC.strerror(Native.getLastError()));
                }

                // ... and increase it by 1.  For some reason we have to
                // do this (using LIBC), otherwise the following real
                // sysctl call won't return the real data but only some garbage...
                size.setValue(size.getValue() + 1);
                StringArrayMemory m = new StringArrayMemory(size.getValue());
                if (LIBC.sysctl(new int[]{CTL_KERN, KERN_PROCARGS2, pid}, 3, m, size, NULL, newSize) != 0) {
                    throw new IOException("Failed to obtain kern.procargs2: " + LIBC.strerror(Native.getLastError()));
                }

                /*
                 * Make a sysctl() call to get the raw argument space of the
                 * process.  The layout is documented in start.s, which is part
                 * of the Csu project.  In summary, it looks like:
                 *
                 * /---------------\ 0x00000000
                 * :               :
                 * :               :
                 * |---------------|
                 * | argc          |
                 * |---------------|
                 * | arg[0]        |
                 * |---------------|
                 * :               :
                 * :               :
                 * |---------------|
                 * | arg[argc - 1] |
                 * |---------------|
                 * | 0             |
                 * |---------------|
                 * | env[0]        |
                 * |---------------|
                 * :               :
                 * :               :
                 * |---------------|
                 * | env[n]        |
                 * |---------------|
                 * | 0             |
                 * |---------------| <-- Beginning of data returned by sysctl()
                 * | exec_path     |     is here.
                 * |:::::::::::::::|
                 * |               |
                 * | String area.  |
                 * |               |
                 * |---------------| <-- Top of stack.
                 * :               :
                 * :               :
                 * \---------------/ 0xffffffff
                 */

                int nargs = m.readInt();
                m.readString(); // exec path
                for (int i = 0; i < nargs; i++) {
                    m.skip0();
                    arguments.add(m.readString());
                }

                // this is how you can read environment variables
                while (m.hasMore() && m.peek() != 0) {
                    String line = m.readString();
                    int sep = line.indexOf('=');
                    if (sep > 0) {
                        envVars.put(line.substring(0, sep), line.substring(sep + 1));
                    }
                }
            } catch (IOException e) {
                // this happens with insufficient permissions, so just ignore the problem.
            }
        }
    }

    @Extension
    public static class DescriptorImpl extends NativeMacSupportDescriptor {

        @Override
        public String getDisplayName() {
            return "JNA based Native Mac Support";
        }
    }
}
