package org.jeecf.manager.module.gen.model.query;

import java.io.Serializable;
import java.util.Date;

import org.jeecf.manager.module.gen.model.domian.SysTreeDict;

/**
 * 树组字典 查询实体
 * 
 * @author jianyiming
 *
 */
public class SysTreeDictQuery extends SysTreeDict implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * 创建时间开始时间
     */
    private Date beginCreateDate;
    /**
     * 创建时间结束时间
     */
    private Date endCreateDate;
    /**
     * 更新时间开始时间
     */
    private Date beginUpdateDate;
    /**
     * 更新时间结束时间
     */
    private Date endUpdateDate;

    public Date getBeginCreateDate() {
        return beginCreateDate;
    }

    public void setBeginCreateDate(Date beginCreateDate) {
        this.beginCreateDate = beginCreateDate;
    }

    public Date getEndCreateDate() {
        return endCreateDate;
    }

    public void setEndCreateDate(Date endCreateDate) {
        this.endCreateDate = endCreateDate;
    }

    public Date getBeginUpdateDate() {
        return beginUpdateDate;
    }

    public void setBeginUpdateDate(Date beginUpdateDate) {
        this.beginUpdateDate = beginUpdateDate;
    }

    public Date getEndUpdateDate() {
        return endUpdateDate;
    }

    public void setEndUpdateDate(Date endUpdateDate) {
        this.endUpdateDate = endUpdateDate;
    }

}
