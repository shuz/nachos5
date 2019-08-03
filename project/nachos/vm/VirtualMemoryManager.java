package nachos.vm;

import java.util.Hashtable;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.threads.KThread;
import nachos.threads.Lock;

public class VirtualMemoryManager {

    public VirtualMemoryManager(String filename) {
	lock.acquire();
	int pages = Machine.processor().getMemory().length / Processor.pageSize;
	invertedPageTable = new TableEntry[pages];
	for (int i = 0; i < pages; ++i) {
	    invertedPageTable[i] = new TableEntry(i);
	}
	pagefile = new PageFile(filename);
	lock.release();
    }
    
    public void dispose() {
	pagefile.close();
    }
    
    private static class TableEntry {
	TableEntry(int i) {
	    entry.ppn = i;
	    entry.valid = false;
	}
	TranslationEntry entry = new TranslationEntry();
	int pid;
	boolean inuse;
    }
    
    private TranslationEntry getFreePage() {
	Lib.assert(lock.isHeldByCurrentThread());
	TranslationEntry []entries = VMKernel.memoryManager.allocPages(1);
	TranslationEntry entry = null;
	if (entries != null) {
	    int ppn = entries[0].ppn;
	    entry = invertedPageTable[ppn].entry;
	    entry.ppn = ppn;
	    entry.dirty = false;
	    entry.valid = false;	// set to true later
	} else {
            int steps = 0;
            int pid = -1;
            while (true) {
                TableEntry vaddr = invertedPageTable[clockNeedle];
                TranslationEntry cur = vaddr.entry;
                if (!vaddr.inuse) {
                    if (cur.used) 
                        cur.used = false;
                    else {
                        entry = cur;
                        pid = vaddr.pid;
                        break;
                    }
                }
                clockNeedle = (clockNeedle + 1) % invertedPageTable.length;
                if (steps == invertedPageTable.length + 2) {
                    // already turned one round + 2 page, still nothing found
                    // this may happen only many pages are in use
                    steps = 0;
                    lock.release();
                    KThread.yield();
                    lock.acquire();
                }
                ++steps;
            }
            if (entry.valid) {
                if (entry.dirty) {
            	    if (!pagefile.write(pid, entry)) {
            		return null;
            	    }
            	    releasePageFromPhysicalMemory(entry.ppn);
                } else {
                    Lib.assert(entryInSwap(pid, entry.vpn));
                }
            }
	}

	Lib.assert(invertedPageTable[entry.ppn].entry == entry);
	
	return entry;
    }
    
    private void releasePageFromPhysicalMemory(int ppn) {
	Lib.assert(lock.isHeldByCurrentThread());
	TableEntry entry = invertedPageTable[ppn];
	Lib.assert(entry.inuse == false);
	Lib.assert(entry.entry.valid);
	VirtualAddress key = new VirtualAddress(entry.pid, entry.entry.vpn);
	Lib.assert(hashtable.containsKey(key));
	hashtable.remove(key);
	entry.entry.valid = false;
    }
    
    public void freePage(int pid, int vpn) {
	Lib.assert(lock.isHeldByCurrentThread());
	if (entryInSwap(pid, vpn)) {
	    pagefile.free(pid, vpn);
	} else {
	    TableEntry vaddr = getVaddr(pid, vpn);

	    TranslationEntry [] pages = new TranslationEntry[1];
	    pages[0] = vaddr.entry;
	    VMKernel.memoryManager.deallocPages(pages);
	    releasePageFromPhysicalMemory(vaddr.entry.ppn);
	}
    }
    
    public TranslationEntry getEntry(int pid, int vpn) {
	Lib.assert(lock.isHeldByCurrentThread());
	TableEntry vaddr = getVaddr(pid, vpn);
	return (vaddr == null) ? null : vaddr.entry;
    }
    
    public TranslationEntry getEntry(int ppn) {
	Lib.assert(lock.isHeldByCurrentThread() || 
		invertedPageTable[ppn].inuse == true);
	return invertedPageTable[ppn].entry;
    }
    
    private TableEntry getVaddr(int pid, int vpn) {
	Lib.assert(lock.isHeldByCurrentThread());
	return (TableEntry)hashtable.get(new VirtualAddress(pid, vpn));
    }
    
    /*
     * load page into memory and lock it
     * return its ppn
     */
    public int lockPage(int pid, int vpn, boolean newpage) {
	lock.acquire();
	TableEntry vaddr = getVaddr(pid, vpn);
	if (vaddr == null) {
	   int ppn = loadPage(pid, vpn, newpage);
	   if (ppn == -1) {
	       lock.release();
	       return -1;
	   }
	   vaddr = invertedPageTable[ppn];
	}
	vaddr.inuse = true;
	lock.release();
	return vaddr.entry.ppn;
    }
    
    public void unlockPage(int ppn) {
	lock.acquire();
	TableEntry vaddr = invertedPageTable[ppn];
	vaddr.inuse = false;
	lock.release();
    }
    
    public int loadPage(int pid, int vpn, boolean newpage) {
	Lib.assert(lock.isHeldByCurrentThread());
	Lib.assert(getVaddr(pid, vpn) == null);
	TranslationEntry freepage = getFreePage();
	if (freepage == null) {
	    return -1;
	}
	
	freepage.vpn = vpn;
	freepage.valid = true;
	freepage.dirty = newpage? true : false;
	freepage.used = true;
	freepage.readOnly = false;
	invertedPageTable[freepage.ppn].pid = pid;
	Lib.assert(invertedPageTable[freepage.ppn].entry == freepage);
	if (!newpage && !pagefile.read(pid, vpn, freepage.ppn)) {
	    TranslationEntry [] pages = new TranslationEntry[1];
	    pages[0] = freepage;
	    VMKernel.memoryManager.deallocPages(pages);
	    releasePageFromPhysicalMemory(freepage.ppn);
	    return -1;
	}
	VirtualAddress key = new VirtualAddress(pid, vpn); 
	Lib.assert(!hashtable.containsKey(key));
	hashtable.put(key, invertedPageTable[freepage.ppn]);
	return freepage.ppn;
    }
    
    public boolean entryInSwap(int pid, int vpn) {
	return pagefile.containsEntry(pid, vpn);
    }
    
    public boolean entryInMem(int pid, int vpn) {
	return hashtable.containsKey(new VirtualAddress(pid, vpn));
    }
    
    public boolean contains(int pid, int vpn) {
	return entryInSwap(pid, vpn) || entryInMem(pid, vpn);
    }
    
    public void lock() {
	lock.acquire();
	locked = true;
    }
    
    public void unlock() {
	locked = false;
	lock.release();
    }
    
    public boolean lockHolding() {
	return lock.isHeldByCurrentThread();
    }    
    
    private TableEntry [] invertedPageTable;
    private PageFile pagefile = null;
    private Lock lock = new Lock();
    private boolean locked = false;
    private int clockNeedle = 0;
    private Hashtable hashtable = new Hashtable();
}
