package nachos.vm;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public VMProcess() {
	super();
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
	super.saveState();
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	boolean intStatus = Machine.interrupt().disable();
	for (int i = 0; i < Machine.processor().getTLBSize(); ++i) {
	    TranslationEntry entry = new TranslationEntry();
	    Lib.assert(entry.valid == false);
	    Machine.processor().writeTLBEntry(i, entry);
	}
	Machine.interrupt().restore(intStatus);
    }
    
    /**
     * Initializes page tables for this process so that the executable can
     * be demand-paged.
     * 
     * @return <tt>true</tt> if successful.
     */
    protected boolean loadSections() {
	sectionTable = new int[numPages];
	int vpn = 0;
	for (int s = 0; s < coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);

	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		    + " section (" + section.getLength() + " pages)");

	    for (int i = 0; i < section.getLength(); i++) {
		vpn = section.getFirstVPN() + i;
		sectionTable[vpn] = s;
	    }
	}
	for (++vpn; vpn < numPages; ++vpn) {
	    sectionTable[vpn] = -1;
	}
	int ppn = lockPage(numPages-2, true);	// first stack page
	clearPage(ppn);
	unlockPage(ppn);
	ppn = lockPage(numPages-1, true);
	clearPage(ppn);
	unlockPage(ppn);
	
	return true;
    }

    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
	Lib.assert(offset >= 0 && length >= 0
			&& offset + length <= data.length);

	byte[] memory = Machine.processor().getMemory();

	int vpn = vaddr / pageSize;
	int pageOffset = vaddr % pageSize;
	int bytesLeft = length;

	while (bytesLeft > 0 && 0 <= vpn && vpn < numPages) {
	    int ppn = loadPage(vpn);

	    Lib.assert(getEntryPPN(ppn).vpn == vpn);
	    int amount = Math.min(bytesLeft, pageSize - pageOffset);
	    TranslationEntry page = getEntryPPN(ppn);
	    page.used = true;
	    int paddr = ppn * pageSize + pageOffset;
	    System.arraycopy(memory, paddr, data, offset, amount);

	    Lib.assert(page.vpn == vpn);
	    ++vpn;
	    pageOffset = 0;
	    bytesLeft -= amount;
	    offset += amount;

	    unlockPage(ppn);
	}

	return length - bytesLeft;
    }

    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
	Lib.assert(offset >= 0 && length >= 0
			&& offset + length <= data.length);

	byte[] memory = Machine.processor().getMemory();

	int vpn = vaddr / pageSize;
	int pageOffset = vaddr % pageSize;
	int bytesLeft = length;

	while (bytesLeft > 0 && 0 <= vpn && vpn < numPages) {
	    int ppn = loadPage(vpn);
	    
	    Lib.assert(getEntryPPN(ppn).vpn == vpn);
	    int amount = Math.min(bytesLeft, pageSize - pageOffset);
	    TranslationEntry page = getEntryPPN(ppn);
	    if (page.readOnly)
		break;

	    page.used = true;
	    page.dirty = true;
	    int paddr = ppn * pageSize + pageOffset;
	    System.arraycopy(data, offset, memory, paddr, amount);

	    Lib.assert(page.vpn == vpn);
	    ++vpn;
	    pageOffset = 0;
	    bytesLeft -= amount;
	    offset += amount;
	    
	    unlockPage(ppn);
	}

	return length - bytesLeft;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
	VMKernel.virtualMemoryManager.lock();
	for (int i = 0; i < numPages; ++i) {
	    if (VMKernel.virtualMemoryManager.contains(getProcessID(), i))
		VMKernel.virtualMemoryManager.freePage(getProcessID(), i);
	}
	VMKernel.virtualMemoryManager.unlock();
    }

    private static final int syscallMmap = 10;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>.
     * The <i>syscall</i> argument identifies which syscall the user
     * executed:
     * 
     * <table>
     * <tr>
     * <td>syscall#</td>
     * <td>syscall prototype</td>
     * </tr>
     * <tr>
     * <td>10</td>
     * <td><tt>int  mmap(int fd, char *address);</tt></td>
     * </tr>
     * </table>
     * 
     * @param syscall
     *                the syscall number.
     * @param a0
     *                the first syscall argument.
     * @param a1
     *                the second syscall argument.
     * @param a2
     *                the third syscall argument.
     * @param a3
     *                the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	default:
	    return super.handleSyscall(syscall, a0, a1, a2, a3);
	}
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The <i>cause</i> argument
     * identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     * 
     * @param cause
     *                the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionTLBMiss:
	    handleTLBMiss(Machine.processor().readRegister(
		    Processor.regBadVAddr));
	    break;
	default:
	    super.handleException(cause);
	    break;
	}
    }

    private void handleTLBMiss(int vaddr) {
	int vpn = vaddr / pageSize;

	int ppn = loadPage(vpn);
	TranslationEntry page = getEntryPPN(ppn);
	Lib.assert(page.valid);

	int toRemove = -1;
	for (int i = 0; i < Machine.processor().getTLBSize(); ++i) {
	    TranslationEntry entry = Machine.processor().readTLBEntry(i);
	    if (!entry.valid) {
		toRemove = i;
		break;
	    } else if (!entry.used) {
		toRemove = i;
	    }
	}
	if (toRemove == -1) { // all TLB are valid and used
	    toRemove = Lib.random(Machine.processor().getTLBSize());
	}

	Machine.processor().writeTLBEntry(toRemove, page);

	unlockPage(ppn);
    }

    private int loadPage(int vpn) {
	if (vpn >= numPages)
	    doExit(true, 0);
	int ppn;
	if (VMKernel.virtualMemoryManager.contains(getProcessID(), vpn)) {
	    ppn = lockPage(vpn, false);
	} else {
	    int s = sectionTable[vpn];
	    boolean readonly = false;
	    if (s == -1) {
		ppn = lockPage(vpn, true);
		clearPage(ppn);
	    } else {
                CoffSection section = coff.getSection(s);
                int i = vpn - section.getFirstVPN();
                
                ppn = lockPage(vpn, true);
                section.loadPage(i, ppn);
                readonly = section.isReadOnly();
	    }
	    TranslationEntry entry = getEntryPPN(ppn);
	    entry.dirty = true;
	    entry.readOnly = readonly;
	}
	if (ppn == -1) doExit(true, 0);
	return ppn;
    }
    
    private void clearPage(int ppn) {
	byte [] memory = Machine.processor().getMemory();
	int paddr = ppn * pageSize;
	Arrays.fill(memory, paddr, paddr+pageSize, (byte)0);
    }

    private int lockPage(int vpn, boolean newpage) {
	int ppn = VMKernel.virtualMemoryManager.lockPage(getProcessID(), vpn, newpage);
	if (ppn == -1) {
	    doExit(true, 0);
	}
	return ppn;
    }

    private void unlockPage(int ppn) {
	VMKernel.virtualMemoryManager.unlockPage(ppn);
    }

    private TranslationEntry getEntryVPN(int vpn) {
	return VMKernel.virtualMemoryManager.getEntry(getProcessID(), vpn);
    }

    private TranslationEntry getEntryPPN(int ppn) {
	return VMKernel.virtualMemoryManager.getEntry(ppn);
    }

    private int[] sectionTable;

    private static final int pageSize = Processor.pageSize;

    private static final char dbgProcess = 'a';
}
