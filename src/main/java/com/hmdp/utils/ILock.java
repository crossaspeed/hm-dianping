package com.hmdp.utils;

public interface ILock {

    public boolean tyeLock(Long timeoutSec);

    public void unLock();
}
