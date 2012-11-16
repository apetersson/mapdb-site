package org.mapdb;


import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;


public class StorageTransTest extends TestFile {

    final int stackId = StorageDirect.RECID_FREE_PHYS_RECORDS_START+1;

    @Test public void long_stack_reuse() throws IOException {
        
        StorageDirect r = new StorageDirect(fac,true,false,false,false);
        for(int i=1;i<1000;i++){
            r.longStackPut(stackId,i);
        }
        r.close();

        StorageTrans t = new StorageTrans(fac,true,false,false,false);
        for(int i=999;i!=0;i--){
            assertEquals(i, t.longStackTake(stackId));
        }
    }

    @Test public void transaction_basics() throws IOException {
        
        StorageDirect r = new StorageDirect(fac);
        long recid = r.recordPut("aa",Serializer.STRING_SERIALIZER);
        r.close();
        StorageTrans t = new StorageTrans(fac,true,true,false,false);
        assertEquals("aa", t.recordGet(recid, Serializer.STRING_SERIALIZER));
        t.recordUpdate(recid,"bb", Serializer.STRING_SERIALIZER);
        assertEquals("bb", t.recordGet(recid, Serializer.STRING_SERIALIZER));
        t.recordUpdate(recid,"ccc", Serializer.STRING_SERIALIZER);
        assertEquals("ccc", t.recordGet(recid, Serializer.STRING_SERIALIZER));

        long recid2 = t.recordPut("ZZ",Serializer.STRING_SERIALIZER);
        assertEquals("ZZ", t.recordGet(recid2, Serializer.STRING_SERIALIZER));
        t.recordUpdate(recid2,"ZZZ", Serializer.STRING_SERIALIZER);
        assertEquals("ZZZ", t.recordGet(recid2, Serializer.STRING_SERIALIZER));

        t.recordDelete(recid);
        t.recordDelete(recid2);
        assertEquals(null, t.recordGet(recid, Serializer.STRING_SERIALIZER));
        assertEquals(null, t.recordGet(recid2, Serializer.STRING_SERIALIZER));
    }

    @Test public void persisted() throws IOException {
        
        StorageTrans t = new StorageTrans(fac);
        final long recid = t.recordPut("aa",Serializer.STRING_SERIALIZER);
        t.commit();
        t.close();
        t = new StorageTrans(fac);
        assertEquals("aa", t.recordGet(recid, Serializer.STRING_SERIALIZER));

        t.recordUpdate(recid, "bb", Serializer.STRING_SERIALIZER);
        t.commit();
        t.close();
        t = new StorageTrans(fac);
        assertEquals("bb", t.recordGet(recid, Serializer.STRING_SERIALIZER));

        t.recordDelete(recid);
        t.commit();
        t.close();
        t = new StorageTrans(fac);
        assertEquals(null,t.recordGet(recid,Serializer.STRING_SERIALIZER));

    }

    @Test public void long_stack_put_take_nocommit() throws IOException {
        
    }

    @Test public void long_stack_put_take() throws IOException {
        
        StorageTrans t = new StorageTrans(fac,true,false,false,false);
        t.longStackPut(Storage.RECID_FREE_PHYS_RECORDS_START+1, 112L);
        t.commit();
        t.close();
        t = new StorageTrans(fac,true,false,false,false);
        assertEquals(112L, t.longStackTake(Storage.RECID_FREE_PHYS_RECORDS_START + 1));

        t.commit();
        t.close();
        t = new StorageTrans(fac,true,false,false,false);
        assertEquals(0L, t.longStackTake(Storage.RECID_FREE_PHYS_RECORDS_START+1));
    }

    @Test public void index_page_created_from_empty() throws IOException {
        
        StorageTrans t = new StorageTrans(fac,true,false,false,false);
        t.longStackPut(Storage.RECID_FREE_PHYS_RECORDS_START+1, 112L);
        t.commit();
        t.close();
        t = new StorageTrans(fac);
    }


    @Test public void delete_file_on_exit() throws IOException {
        StorageTrans t = new StorageTrans(fac,false,false,true,false);
        t.recordPut("t",Serializer.STRING_SERIALIZER);
        t.close();
        assertFalse(index.exists());
        assertFalse(data.exists());
        assertFalse(log.exists());
    }


    @Test public void log_discarted_after_failed_transaction_reopened() throws IOException {
        StorageTrans t = new StorageTrans(fac);
        long recid1 = t.recordPut("t",Serializer.STRING_SERIALIZER);
        assertTrue(log.exists());
        t.commit();
        if(!Utils.isWindows())
            assertFalse(log.exists());
        long recid2 = t.recordPut("t",Serializer.STRING_SERIALIZER);
        assertTrue(log.exists());
        t.index.sync();
        t.index.close();
        t.phys.sync();
        t.phys.close();
        t.transLog.sync();
        assertEquals(0L, t.transLog.getLong(8));
        t.transLog.close();
        StorageTrans t2 = new StorageTrans(fac);
        assertEquals("t",t2.recordGet(recid1, Serializer.STRING_SERIALIZER));
        assertEquals(null,t2.recordGet(recid2, Serializer.STRING_SERIALIZER));

        if(!Utils.isWindows())
            assertFalse(log.exists());

    }

    @Test(expected = IllegalAccessError.class) @Ignore //TODO
    public void fail_direct_storage_open_if_log_file_exists() throws IOException {
        
        StorageDirect d = new StorageDirect(fac);
        d.recordPut(111L, Serializer.LONG_SERIALIZER);
        d.close();
        log.createNewFile();
        d = new StorageDirect(fac);

    }

    @Test public void replay_log_on_reopen() throws IOException {
        

        StorageTrans t = new StorageTrans(fac){
            @Override
            protected void replayLogFile() {
                //do nothing!
            }
        };

        long recid = t.recordPut(1L, Serializer.LONG_SERIALIZER);
        t.commit();
        t.close();
        assertTrue(log.exists());

        t = new StorageTrans(fac);
        assertEquals(Long.valueOf(1), t.recordGet(recid, Serializer.LONG_SERIALIZER));
        if(!Utils.isWindows())
            assertFalse(log.exists());
    }

    @Test public void log_discarted_on_rollback() throws IOException {
        
        StorageTrans t = new StorageTrans(fac,true,false,false,false);

        long recid = t.recordPut(1L, Serializer.LONG_SERIALIZER);
        assertTrue(log.exists());
        t.rollback();
        assertFalse(log.exists());
        assertEquals(null, t.recordGet(recid,Serializer.LONG_SERIALIZER));
        t.close();
        if(!Utils.isWindows())
            assertFalse(log.exists());
    }



    @Test public void test_long_stack_puts_record_size_into_index() throws IOException {
        StorageTrans engine = new StorageTrans(fac);
        engine.lock.writeLock().lock();
        engine.longStackPut(stackId, 1);
        assertEquals(Storage.LONG_STACK_PAGE_SIZE,engine.recordIndexVals.get(stackId)>>>48);
    }

    @Test public void test_long_stack_put_take() throws IOException {
        StorageTrans engine = new StorageTrans(fac);
        engine.lock.writeLock().lock();

        final long max = 150;
        for(long i=1;i<max;i++){
            engine.longStackPut(stackId, i);
        }

        for(long i = max-1;i>0;i--){
            assertEquals(i, engine.longStackTake(stackId));
        }

        assertEquals(Long.valueOf(0), engine.recordIndexVals.get(stackId));
    }

    @Test public void test_long_stack_put_take_simple() throws IOException {
        StorageTrans engine = new StorageTrans(fac);
        engine.lock.writeLock().lock();
        engine.longStackPut(stackId, 111);
        long pageId = engine.recordIndexVals.get(stackId)&Storage.PHYS_OFFSET_MASK;
        assertEquals(1L<<(8*7), engine.longStackPages.get(pageId)[0]);
        assertEquals(111L, engine.longStackTake(stackId));
        assertNull(engine.longStackPages.get(pageId));
        assertEquals(Long.valueOf(0), engine.recordIndexVals.get(stackId));
    }


    @Test public void test_basic_long_stack() throws IOException {
        StorageTrans engine = new StorageTrans(fac);
        //dirty hack to make sure we have lock
        engine.lock.writeLock().lock();
        final long max = 150;
        ArrayList<Long> list = new ArrayList<Long>();
        for(long i=1;i<max;i++){
            engine.longStackPut(stackId, i);
            list.add(i);
        }

        Collections.reverse(list);

        ArrayList<Long> list2 = new ArrayList<Long>();
        long pageId = engine.recordIndexVals.get(stackId)&Storage.PHYS_OFFSET_MASK;

        while(true){
            long[] page = engine.longStackPages.get(pageId);
            long count = page[0]>>>(7*8);
            for(int i = (int) count; i>0; i--){
                list2.add(page[i]);
            }
            pageId = page[0] & Storage.PHYS_OFFSET_MASK;
            if(pageId==0) break;
        }
        assertEquals(list, list2);
    }


    @Test public void long_stack_page_created_after_put() throws IOException {
        StorageTrans engine = new StorageTrans(fac);
        engine.lock.writeLock().lock();
        engine.longStackPut(stackId, 111);

        long pageId = engine.recordIndexVals.get(stackId);
        assertEquals(StorageDirect.LONG_STACK_PAGE_SIZE, pageId>>>48);
        pageId = pageId & StorageDirect.PHYS_OFFSET_MASK;
        assertEquals(8L, pageId);
        long[] b = engine.longStackPages.get(pageId);
        assertEquals(1, b[0]>>>(7*8));
        assertEquals(0, b[0] & StorageDirect.PHYS_OFFSET_MASK);
        assertEquals(111, b[1]);
    }

    @Test public void long_stack_put_five() throws IOException {
        StorageTrans engine = new StorageTrans(fac);
        engine.lock.writeLock().lock();
        engine.longStackPut(stackId, 111);
        engine.longStackPut(stackId, 112);
        engine.longStackPut(stackId, 113);
        engine.longStackPut(stackId, 114);
        engine.longStackPut(stackId, 115);

        long pageId = engine.recordIndexVals.get(stackId);
        assertEquals(StorageDirect.LONG_STACK_PAGE_SIZE, pageId>>>48);
        pageId = pageId & StorageDirect.PHYS_OFFSET_MASK;
        assertEquals(8L, pageId);
        long[] b = engine.longStackPages.get(pageId);
        assertEquals(5, b[0]>>>(7*8));
        assertEquals(0, b[0] & StorageDirect.PHYS_OFFSET_MASK);
        assertEquals(111, b[1]);
        assertEquals(112, b[2]);
        assertEquals(113, b[3]);
        assertEquals(114, b[4]);
        assertEquals(115, b[5]);
        assertEquals(0, b[6]);
    }

    @Test public void long_stack_page_deleted_after_take() throws IOException {
        StorageTrans engine = new StorageTrans(fac);
        engine.lock.writeLock().lock();
        engine.longStackPut(stackId, 111);
        long pageId =engine.recordIndexVals.get(stackId) & Storage.PHYS_OFFSET_MASK;
        assertNotNull(engine.longStackPages.get(pageId));
        assertEquals(111L, engine.longStackTake(stackId));
        assertNull(engine.longStackPages.get(pageId));
        assertEquals(Long.valueOf(0),engine.recordIndexVals.get(stackId));
    }

    @Test public void long_stack_page_overflow() throws IOException {
        StorageTrans engine = new StorageTrans(fac);
        engine.lock.writeLock().lock();
        //fill page until near overflow
        for(int i=0;i< StorageDirect.LONG_STACK_NUM_OF_RECORDS_PER_PAGE;i++){
            engine.longStackPut(stackId, 1000L+i);
        }


        //check content
        long pageId = engine.recordIndexVals.get(stackId);
        assertEquals(StorageDirect.LONG_STACK_PAGE_SIZE, pageId>>>48);
        pageId = pageId & StorageDirect.PHYS_OFFSET_MASK;
        assertEquals(8L, pageId);
        long[] b = engine.longStackPages.get(pageId);
        assertEquals(StorageDirect.LONG_STACK_NUM_OF_RECORDS_PER_PAGE, b[0]>>>(7*8));
        for(int i=0;i< StorageDirect.LONG_STACK_NUM_OF_RECORDS_PER_PAGE;i++){
            assertEquals(1000L+i, b[i+1]);
        }

        //add one more item, this will trigger page overflow
        engine.longStackPut(stackId, 11L);

        //check page overflowed
        pageId = engine.recordIndexVals.get(stackId);
        assertEquals(StorageDirect.LONG_STACK_PAGE_SIZE, pageId>>>48);
        pageId = pageId & StorageDirect.PHYS_OFFSET_MASK;
        assertEquals(8L+ StorageDirect.LONG_STACK_PAGE_SIZE, pageId);
        b = engine.longStackPages.get(pageId);
        assertEquals(1, b[0]>>>(7*8));
        assertEquals(8L, b[0] & StorageDirect.PHYS_OFFSET_MASK);
        assertEquals(11L, b[1]);
    }


}
