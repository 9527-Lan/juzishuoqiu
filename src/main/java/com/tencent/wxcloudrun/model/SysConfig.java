package com.tencent.wxcloudrun.model;

import lombok.Data;
import java.util.Date;

@Data
public class SysConfig {
    private Long id;
    private String configKey;
    private String configValue;
    private String configDesc;
    private Date updateTime;
}
