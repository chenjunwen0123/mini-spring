package com.whi5p3r.spring.beans.config;

import com.whi5p3r.spring.annotations.Scope;

public enum ScopeType {
    SINGLETON(1),
    PROTOTYPE(2)
    ;
    final int key;
    ScopeType(int key){
        this.key = key;
    }
}
