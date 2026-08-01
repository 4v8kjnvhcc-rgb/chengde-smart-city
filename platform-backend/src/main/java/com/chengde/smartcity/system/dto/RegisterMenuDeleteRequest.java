package com.chengde.smartcity.system.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class RegisterMenuDeleteRequest {

    @NotEmpty
    private List<Long> ids;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}
