What for?
=========

MapDB works fairly well in most situation. It combines speed and simplicity of Java collection with scalability of database engine. Thanks to its versatility if could be used in many on traditional situations.

This chapter enumerates some usages and outlines good approach. It is TL;DR for this manual.

Large in-memory collections
---------------------------

Traditional collections hit GC limit around 100M entries. MapDB stores data outside heap so it does not share this limitation. It works just fine with 10.000M entries and 1TB in-memory store. It also adds some sugar to manage such data.

First rule is to minimize memory usage. One should always use specialized key/value serializers. TreeMap has several options to compress keys (delta, common prefix...) Also large values can be independently compressed.

To import, export and backup large collections one should use data pump. It knows internal structure of collection and uses streaming. Data pump performance does not degrade with size, it can import billion entries in minutes/hours.

For in-memory store it is recomended to disable transactions. However snapshots are independent, so one could use snapshots to backup store to on-disk.

Cache with expiration
---------------------

HashMap has optonal expiraton based on time-to-live, maximal collection size or maximal memory usage. It also has disk overflow, one can have a two level cache (in-memory, on-disk) with different sizes.

On-disk collections
-------------------

By default MapDB uses slow file access based on `RandomAccessFile`. Memory mapped files are much faster, but have problems on 32 bit machines and Windows. On 64bit machine on should use memory mapped files.

There are preallocation options.

Also for on-disk one could use Append Only storage with different format.

By default MapDB has transaction enabled and protects storage from corruption by write-ahead-log. Transactions (WAL) can be disabled to make writes much faster. But in that case there is zero crash protection and store will get corrupted if not closed correctly.

Performance
-----------

By default MapDB uses slow and safe settings.

Always use specialized serializers for keys and values. Generic serialization is ok, but specialized serialization is faster and uses less space.

MapDB has several durability and consistency options. Study those and select level you are comfortable with. It is wrong to protect temporary cache by ACID transactions.

Store is protected by write-ahead-log. This slows down writes, since all data have to be copyed twice. Disable WAL and get faster writes at expense of durability

There is instance cache which speeds up operation a lot. It is disabled by default.

Memory mapped files are much faster. But need 64 bit JVM.

MapDB has background executors for writes, cache eviction etc. All background operations are disabled by default.

Consistency and durability
--------------------------
