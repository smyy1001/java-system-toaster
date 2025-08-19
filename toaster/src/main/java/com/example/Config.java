package com.example;

import com.google.gson.annotations.SerializedName;
import java.util.*;

class RootConfig {
    int defaultPollIntervalSec = 10;
    String stateFile; // state'leri tut (birden fazla kez aynı bildirimi verme)
    List<ServiceCfg> services = new ArrayList<>();
}

class ServiceCfg {
    String name;
    Boolean enabled = Boolean.TRUE;
    Integer pollIntervalSec;
    String iconOverride;
    RequestCfg request = new RequestCfg();
    ParseCfg parse = new ParseCfg();
}

class RequestCfg {
    String url;
    String method = "GET";
    Map<String, String> headers = new HashMap<>();
    Integer timeoutMs = 5000;
    String body;
}

class ParseCfg {
    String updatedField = "updated";
    @SerializedName("updatedIsTrue")
    Boolean updatedIsTrue = Boolean.TRUE;
    String dataPath = "data";
    String idField = "id";
    String titleField = "title";
    String contentField = "content";
    String linkField = "link";
    String iconField = "icon";
}
