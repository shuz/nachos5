package nachos.userprog;

import java.util.LinkedList;
import nachos.machine.TranslationEntry;
import nachos.threads.Lock;

public class MemoryManager {
    /**
     * initialize the memory manager
     * @param n the number of total pages
     */
    public MemoryManager(int n) {
	lock.acquire();
	freePages = new LinkedList();
	for (int i = 0; i < n; ++i) {
	    freePages.addLast(new Integer(i));
	}
	lock.release();
    }
    
    /**
     * Allocate n pages and return their physical page number. 
     * @param n the number of pages to be allocated.
     * @return the translation entry of allocated pages, null if failed
     */
    public TranslationEntry[] allocPages(int n) {
	return allocPages(0, n);
    }
    
    /**
     * Allocate n pages and return their physical page number. 
     * @param vpn the start virtual page number
     * @param n the number of pages to be allocated
     * @return the translation entry of allocated pages, null if failed
     */
    public TranslationEntry[] allocPages(int vpn, int n) {
	lock.acquire();
	if (freePages.size() < n) {
	    lock.release();
	    return null;
	}
	TranslationEntry[] pages = new TranslationEntry[n];
	for (int i = 0; i < n; ++i) {
	    int ppn = ((Integer)freePages.removeFirst()).intValue(); 
	    pages[i] = new TranslationEntry(vpn+i, ppn, false, false, false, false);
	}
	lock.release();
	return pages;
    }
    
    public void deallocPages(TranslationEntry[] pages) {
	if (pages == null) return;
	
	lock.acquire();
	for (int i = 0; i < pages.length; ++i) {
	    if (pages[i].valid)
		freePages.addLast(new Integer(pages[i].ppn));
	}
	lock.release();
    }
    
    public int freepageCount() {
	return freePages.size();
    }
    
    private Lock lock = new Lock();
    private LinkedList freePages;
}
