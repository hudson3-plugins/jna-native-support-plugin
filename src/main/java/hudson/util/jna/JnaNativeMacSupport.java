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

import static hudson.util.jna.GNUCLibrary.LIBC;
import com.sun.jna.Native;
import java.io.IOException;
import java.util.ArrayList;

import static com.sun.jna.Pointer.NULL;

/**
 *
 *  JNA based Native Support Extension for Hudson
 */
public class JnaNativeMacSupport extends NativeMacSupport {

    private static final Logger LOGGER = Logger.getLogger(JnaNativeMacSupport.class.getName());

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

     
    // local constants
    private static final int sizeOf_kinfo_proc = 492; // TODO:checked on 32bit Mac OS X. is this different on 64bit?
    private static final int sizeOfInt = Native.getNativeSize(int.class);
    private static final int CTL_KERN = 1;
    private static final int KERN_PROC = 14;
    private static final int KERN_PROC_ALL = 0;
    private static final int ENOMEM = 12;
    private static int[] MIB_PROC_ALL = {CTL_KERN, KERN_PROC, KERN_PROC_ALL};
    private static final int KERN_ARGMAX = 8;
    private static final int KERN_PROCARGS2 = 49;

    @Override
    public List<NativeProcess> getMacProcesses() throws NativeAccessException {
        List<NativeProcess> processList = new ArrayList<NativeProcess>();
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
                int pid = m.getInt(base + 24);
                int ppid = m.getInt(base + 416);
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

    private static class NativeMacProcess implements NativeProcess {

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

        public String getCommandLine() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public Map<String, String> getEnvironmentVariables() {
            if (envVars != null) {
                return envVars;
            }
            parse();
            return envVars;
        }

        private void parse() {
            try {
                // allocate them first, so that the parse error wil result in empty data
                // and avoid retry.
                arguments = new ArrayList<String>();
                envVars = new HashMap<String, String>();

                IntByReference _ = new IntByReference();

                IntByReference argmaxRef = new IntByReference(0);
                IntByReference size = new IntByReference(sizeOfInt);

                // for some reason, I was never able to get sysctlbyname work.
                // if(LIBC.sysctlbyname("kern.argmax", argmaxRef.getPointer(), size, NULL, _)!=0)
                if (LIBC.sysctl(new int[]{CTL_KERN, KERN_ARGMAX}, 2, argmaxRef.getPointer(), size, NULL, _) != 0) {
                    throw new IOException("Failed to get kernl.argmax: " + LIBC.strerror(Native.getLastError()));
                }

                int argmax = argmaxRef.getValue();

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

                    byte peek() {
                        return getByte(offset);
                    }

                    String readString() {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte ch;
                        while ((ch = getByte(offset++)) != '\0') {
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
                StringArrayMemory m = new StringArrayMemory(argmax);
                size.setValue(argmax);
                if (LIBC.sysctl(new int[]{CTL_KERN, KERN_PROCARGS2, pid}, 3, m, size, NULL, _) != 0) {
                    throw new IOException("Failed to obtain ken.procargs2: " + LIBC.strerror(Native.getLastError()));
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
                while (m.peek() != 0) {
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
