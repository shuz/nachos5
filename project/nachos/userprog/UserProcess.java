package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
	boolean intStatus = Machine.interrupt().disable();
	++processCount;
	processID = nextProcessID++;
	Machine.interrupt().restore(intStatus);
	
	files[0] = UserKernel.console.openForReading();
	files[1] = UserKernel.console.openForWriting();
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	if (root == null) 
	    root = this;
	
	thread = new UThread(this);
	thread.setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }
    
    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assert(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    public Integer readVirtualMemoryInteger(int vaddr) {
	byte[] bytes = new byte[4];
	
	int bytesRead = readVirtualMemory(vaddr, bytes);
	
	if (bytesRead != 4)
	    return null;
	
	return new Integer(Lib.bytesToInt(bytes, 0));
    }

    public int writeVirtualMemoryInteger(int vaddr, int val) {
	byte[] bytes = new byte[4];
	Lib.bytesFromInt(bytes, 0, val);
	return writeVirtualMemory(vaddr, bytes);
    }
    
    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
	Lib.assert(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	int vpn = vaddr / pageSize;
	int pageOffset = vaddr % pageSize;
	int bytesLeft = length;

	while (bytesLeft > 0 && 0 <= vpn && vpn < pageTable.length) {
	    int amount = Math.min(bytesLeft, pageSize - pageOffset);
            pageTable[vpn].used = true;
            int ppn = pageTable[vpn].ppn;
            int paddr = ppn * pageSize + pageOffset;
            System.arraycopy(memory, paddr, data, offset, amount);

	    Lib.assert(pageTable[vpn].vpn == vpn);
	    ++vpn; pageOffset = 0;
	    bytesLeft -= amount; offset += amount; 
	}

	return length - bytesLeft;
    }
    
    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
	Lib.assert(offset >= 0 && length >= 0 && offset+length <= data.length);
	
	byte[] memory = Machine.processor().getMemory();
	
	int vpn = vaddr / pageSize;
	int pageOffset = vaddr % pageSize;
	int bytesLeft = length;

	while (bytesLeft > 0 && 0 <= vpn && vpn < pageTable.length && !pageTable[vpn].readOnly) {
	    int amount = Math.min(bytesLeft, pageSize - pageOffset);
            pageTable[vpn].used = true;
            pageTable[vpn].dirty = true;
            int ppn = pageTable[vpn].ppn;
            int paddr = ppn * pageSize + pageOffset;
            System.arraycopy(data, offset, memory, paddr, amount);
	    
	    Lib.assert(pageTable[vpn].vpn == vpn);
	    ++vpn; pageOffset = 0;
	    bytesLeft -= amount; offset += amount; 
	}

	return length - bytesLeft;
    }
    
    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assert(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assert(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assert(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
	pageTable = UserKernel.memoryManager.allocPages(numPages);
	if (pageTable == null) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}

	int vpn = 0;
	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");

	    boolean readOnly = section.isReadOnly();
	    
	    for (int i=0; i<section.getLength(); i++) {
		vpn = section.getFirstVPN()+i;

		section.loadPage(i, pageTable[vpn].ppn);
		pageTable[vpn].readOnly = readOnly;
		pageTable[vpn].valid = true;
	    }
	}
	
	for (vpn++; vpn < numPages; ++vpn) {
	    pageTable[vpn].valid = true;
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
	UserKernel.memoryManager.deallocPages(pageTable);
	pageTable = null;
    }
    
    public int getProcessID() {
	return processID;
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }


    private static final int
        syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	case syscallHalt:
	    doHalt();
	    return 0;
	case syscallExit:
	    doExit(false, a0);
	    return 0;
	case syscallExec:
	    return doExec(a0, a1, a2);
	case syscallJoin:
	    return doJoin(a0, a1);
	case syscallCreate:
	    return doOpenCreate(a0, true);
	case syscallOpen:
	    return doOpenCreate(a0, false);
	case syscallRead:
	    return doRead(a0, a1, a2);
	case syscallWrite:
	    return doWrite(a0, a1, a2);
	case syscallClose:
	    return doClose(a0);
	case syscallUnlink:
	    return doUnlink(a0);
	}

	Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	doExit(true, 0);
	
	Lib.assertNotReached();
	return 0;
    }


    protected void doHalt() {
        if (this == root) {
            Machine.halt();
            Lib.assertNotReached();
        }
    }

    protected void doExit(boolean abnormal, int status) {
	Iterator itor = children.values().iterator();
	while (itor.hasNext()) {
	    UserProcess proc = (UserProcess)itor.next();
	    proc.parent = null;
	}
	
	if (coff != null) coff.close();
	unloadSections();
	closeFiles();

	// make memory available by setting the following refs to null
	coff = null;
	parent = null;
	children = null;
	
	exitStatus = status;
	abnormalExit = abnormal;
	
	boolean intStatus = Machine.interrupt().disable();
	if (--processCount == 0) {
	    Kernel.kernel.terminate();
	}
	if (thread != null) {
	    Lib.assert(thread == UThread.currentThread());
	    thread = null;
	    UThread.finish();
	    Lib.assertNotReached();
	}
	Machine.interrupt().restore(intStatus);
    }

    protected int doExec(int fileAddr, int argc, int argvAddr) {
	if (argc < 0) return -1;
	String filename = readVirtualMemoryString(fileAddr, MAX_STRING_LENGTH);
	if (filename == null) return -1;
	
	String [] args = new String[argc];
	for (int i = 0; i < argc; ++i, argvAddr += 4) {
	    Integer argiAddrI = readVirtualMemoryInteger(argvAddr);
	    if (argiAddrI == null) return -1;
	    int argiAddr = argiAddrI.intValue();
	    args[i] = readVirtualMemoryString(argiAddr, MAX_STRING_LENGTH);
	    if (args[i] == null) return -1;
	}
	
	UserProcess proc = newUserProcess();
	if (!proc.execute(filename, args)) {
	    proc.doExit(true, 0);
	    return -1;
	}
	addChild(proc);
	
	return proc.processID;
    }

    protected int doJoin(int processID, int statusAddr) {
	UserProcess child = searchChild(processID);
	if (child == null) return -1;
	
	child.thread.join();
	removeChild(processID);
	if (child.abnormalExit) return 0;
	
	if (writeVirtualMemoryInteger(statusAddr, child.exitStatus) != 4) {
	    return -1;
	}
	
	return 1;
    }

    protected int doOpenCreate(int nameAddr, boolean create) {
	int fd = getFileDescriptor();
	if (fd == -1) return -1;
	
	String filename = readVirtualMemoryString(nameAddr, MAX_STRING_LENGTH);
	if (filename == null) return -1;

	files[fd] = UserKernel.fileSystem.open(filename, create);
	if (files[fd] == null) return -1;
	
	return fd;
    }

    protected int doRead(int fd, int bufferAddr, int count) {
	if (files[fd] == null) return -1;
	if (count < 0) return -1;
	
	byte [] bytes = new byte[count];
	
	int bytesRead = files[fd].read(bytes, 0, count);
	if (bytesRead == -1) return -1;
	
	int bytesWritten = writeVirtualMemory(bufferAddr, bytes, 0, bytesRead);
	if (bytesWritten != bytesRead) return -1;
	
	return bytesRead;
    }

    protected int doWrite(int fd, int bufferAddr, int count) {
	if (files[fd] == null) return -1;
	if (count < 0) return -1;
	
	byte [] bytes = new byte[count];
	
	int bytesRead = readVirtualMemory(bufferAddr, bytes, 0, count);
	if (bytesRead != count) {
	    return -1;
	}
	
	return files[fd].write(bytes, 0, count);
    }

    protected int doClose(int fd) {
	if (files[fd] == null) return -1;
	files[fd].close();
	files[fd] = null;
	return 0;
    }

    protected int doUnlink(int nameAddr) {
	String filename = readVirtualMemoryString(nameAddr, MAX_STRING_LENGTH);
	if (filename == null) return -1;
	
	if (!UserKernel.fileSystem.remove(filename))
	    return -1;
	return 0;
    }
    
    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;

	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    doExit(true, 0);
	    Lib.assertNotReached();
	}
    }
    
    protected void addChild(UserProcess proc) {
	children.put(new Integer(proc.processID), proc);
	proc.parent = this;
    }
    
    protected void removeChild(int pid) {
	children.remove(new Integer(pid));
    }
    
    protected UserProcess searchChild(int pid) {
	return (UserProcess)children.get(new Integer(pid));
    }
    
    protected int getFileDescriptor() {
	for (int i = 0; i < MAX_OPEN_FILE; ++i) {
	    if (files[i] == null) return i;
	}
	return -1;
    }
    
    protected void closeFiles() {
	for (int i = 0; i < MAX_OPEN_FILE; ++i) {
	    if (files[i] != null) {
		files[i].close();
	    }
	}
	files = null;
    }
    
    public static void selfTest() {
	// testID 12 : tests readVirtualMemory with a large valid range, make sure read right data 
	// testID 13 : tests writeVirtualMemory with a large valid range, make sure wrote right data 
	
	UserProcess proc = new UserProcess();
	proc.pageTable = UserKernel.memoryManager.allocPages(2);
	proc.pageTable[0].valid = true;
	proc.pageTable[1].valid = true;
	byte[] bytes = new byte[2*pageSize];
	for (int i = 0; i < pageSize/2; ++i) {
	    Lib.bytesFromInt(bytes, 4*i, i);
	}
	
	Lib.assert(proc.writeVirtualMemory(0, bytes, 2*pageSize - 400, 400) == 400);
	Lib.assert(proc.writeVirtualMemory(400, bytes, 0, 2*pageSize - 400) == 2*pageSize - 400);
	
	bytes = new byte[2*pageSize];
	Lib.assert(proc.readVirtualMemory(0, bytes, 2*pageSize - 400, 400) == 400);
	Lib.assert(proc.readVirtualMemory(400, bytes, 0, 2*pageSize - 400) == 2*pageSize - 400);
	
	for (int i = 0; i < pageSize/2; ++i) {
	    Lib.assert(i == Lib.bytesToInt(bytes, 4*i));
	}
	++processCount;
	proc.doExit(false, 0);
	--processCount;
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    private TranslationEntry[] pageTable;
    
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    
    private static UserProcess root = null;
    
    private UserProcess parent = null;
    private Map children = new TreeMap();
    private OpenFile [] files = new OpenFile[MAX_OPEN_FILE];
    private int exitStatus = 0;
    private boolean abnormalExit = false;
    private int processID;
    private UThread thread;
    
    private static final int MAX_STRING_LENGTH = 255;
    private static final int MAX_OPEN_FILE = 16;
    private static int nextProcessID = 1;
    private static int processCount = 0;
}
