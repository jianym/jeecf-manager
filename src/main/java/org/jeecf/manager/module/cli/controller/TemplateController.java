package org.jeecf.manager.module.cli.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.CollectionUtils;
import org.jeecf.common.enums.SplitCharEnum;
import org.jeecf.common.enums.SysErrorEnum;
import org.jeecf.common.exception.BusinessException;
import org.jeecf.common.lang.StringUtils;
import org.jeecf.common.mapper.JsonMapper;
import org.jeecf.common.model.Response;
import org.jeecf.common.utils.IdGenUtils;
import org.jeecf.manager.common.enums.BusinessErrorEnum;
import org.jeecf.manager.common.properties.ThreadLocalProperties;
import org.jeecf.manager.common.utils.DbsourceUtils;
import org.jeecf.manager.common.utils.DownloadUtils;
import org.jeecf.manager.common.utils.NamespaceUtils;
import org.jeecf.manager.common.utils.RedisCacheUtils;
import org.jeecf.manager.common.utils.TemplateUtils;
import org.jeecf.manager.gen.model.GenParams;
import org.jeecf.manager.interceptor.DynamicDataSourceAspect;
import org.jeecf.manager.module.cli.model.AuthModel;
import org.jeecf.manager.module.cli.model.GenModel;
import org.jeecf.manager.module.cli.model.GenSingleModel;
import org.jeecf.manager.module.cli.model.TemplateField;
import org.jeecf.manager.module.cli.model.TemplateInput;
import org.jeecf.manager.module.cli.service.UserAuthService;
import org.jeecf.manager.module.config.model.domain.SysNamespace;
import org.jeecf.manager.module.config.model.result.SysDbsourceResult;
import org.jeecf.manager.module.config.service.SysNamespaceService;
import org.jeecf.manager.module.extend.service.SysOsgiPluginService;
import org.jeecf.manager.module.template.facade.GenTemplateFacade;
import org.jeecf.manager.module.template.model.domain.GenFieldColumn;
import org.jeecf.manager.module.template.model.domain.GenTemplate;
import org.jeecf.manager.module.template.model.po.GenFieldColumnPO;
import org.jeecf.manager.module.template.model.po.GenTemplatePO;
import org.jeecf.manager.module.template.model.query.GenFieldColumnQuery;
import org.jeecf.manager.module.template.model.query.GenTemplateQuery;
import org.jeecf.manager.module.template.model.result.GenFieldColumnResult;
import org.jeecf.manager.module.template.model.result.GenTemplateResult;
import org.jeecf.manager.module.template.service.GenFieldColumnService;
import org.jeecf.manager.module.template.service.GenTemplateService;
import org.jeecf.manager.module.userpower.model.domain.SysUser;
import org.jeecf.manager.module.userpower.model.result.SysUserResult;
import org.jeecf.osgi.enums.BoundleEnum;
import org.jeecf.osgi.enums.LanguageEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 * 命令行模版接口
 * 
 * @author jianyiming
 * @version 2.0
 */
@RestController
@RequestMapping(value = { "cli/tmpl" })
@Api(value = "命令行模版 api", tags = { "命令行模版接口" })
public class TemplateController {

    @Autowired
    private ThreadLocalProperties threadLocalProperties;

    @Autowired
    private UserAuthService userAuthService;

    @Autowired
    private GenTemplateService genTemplateService;

    @Autowired
    private GenTemplateFacade genTemplateFacade;

    @Autowired
    private SysNamespaceService sysNamespaceService;

    @Autowired
    private SysOsgiPluginService sysOsgiPluginService;

    @Autowired
    private GenFieldColumnService genFieldColumnService;

    private static final int RANDOM_MAX = 4;

    private static final String DEFAULT_VERSION = "1.0.0";

    private static final Integer DEFAULT_LANGUAGE = LanguageEnum.JAVA.getCode();

    private static final String CACHE_TMPL_PREFIX = "cli:tmpl:";

    private static final String CACHE_CODE_PREFIX = "cli:code:";

    @PostMapping(value = { "list/{namespace}" })
    @ApiOperation(value = "列表", notes = "返回用户模版列表")
    public Response<List<String>> list(@RequestBody AuthModel authModel, @PathVariable(required = false) String namespace) {
        Response<SysUserResult> sysUserResultRes = userAuthService.auth(authModel);
        SysNamespace sysNamespace = sysNamespaceService.get(sysUserResultRes.getData().getId(), namespace);
        if (sysNamespace != null) {
            List<String> fieldList = new ArrayList<>();
            userAuthService.authPermission(sysUserResultRes.getData().getId(), sysNamespace.getPermission());
            GenTemplateQuery genTemplateQuery = new GenTemplateQuery();
            genTemplateQuery.setSysNamespaceId(Integer.valueOf(sysNamespace.getId()));
            GenTemplatePO genTemplatePO = new GenTemplatePO(genTemplateQuery);
            Response<List<GenTemplateResult>> genTemplateResultListRes = genTemplateService.findList(genTemplatePO);
            if (CollectionUtils.isNotEmpty(genTemplateResultListRes.getData())) {
                genTemplateResultListRes.getData().forEach(genTemplateResult -> {
                    fieldList.add(genTemplateResult.getTemplateName());
                });
            }
            return new Response<>(fieldList);
        }
        throw new BusinessException(BusinessErrorEnum.NAMESPACE_NOT);
    }

    @PostMapping(value = { "pull/{namespace}/{name}", "pull/{name}" })
    @ApiOperation(value = "获取", notes = "获取模版定位")
    public Response<String> pull(@RequestBody AuthModel authModel, @PathVariable(required = false) String namespace, @PathVariable String name, HttpServletResponse response) {
        Response<SysUserResult> sysUserResultRes = userAuthService.auth(authModel);
        SysNamespace sysNamespace = sysNamespaceService.get(sysUserResultRes.getData().getId(), namespace);
        if (sysNamespace != null) {
            userAuthService.authPermission(sysUserResultRes.getData().getId(), sysNamespace.getPermission());
            GenTemplateQuery genTemplateQuery = new GenTemplateQuery();
            genTemplateQuery.setSysNamespaceId(Integer.valueOf(sysNamespace.getId()));
            genTemplateQuery.setTemplateName(name);
            GenTemplatePO genTemplatePO = new GenTemplatePO(genTemplateQuery);
            Response<List<GenTemplateResult>> genTemplateResultListRes = genTemplateService.findList(genTemplatePO);
            if (CollectionUtils.isNotEmpty(genTemplateResultListRes.getData())) {
                GenTemplateResult genTemplateResult = genTemplateResultListRes.getData().get(0);
                String zipFilePath = TemplateUtils.getZipFilePath(genTemplateResult.getFileBasePath(), sysNamespace.getNamespaceName());
                String uuid = IdGenUtils.randomUUID(RANDOM_MAX);
                RedisCacheUtils.setSysCache(CACHE_TMPL_PREFIX + uuid, zipFilePath);
                return new Response<>(uuid);
            }
            throw new BusinessException(BusinessErrorEnum.DATA_NOT_EXIT);
        }
        throw new BusinessException(BusinessErrorEnum.NAMESPACE_NOT);
    }

    @PostMapping(value = { "gen" })
    @ApiOperation(value = "代码生成", notes = "生成代码")
    public Response<String> code(@RequestBody GenModel genModel, HttpServletRequest request, HttpServletResponse response) {
        String userId = userAuthService.login(genModel.getUsername(), genModel.getPassword(), request, response);
        SysNamespace sysNamespace = sysNamespaceService.get(userId, genModel.getNamespace());
        if (sysNamespace != null) {
            SysDbsourceResult sysDbsourceResult = DbsourceUtils.getSysDbsource(genModel.getDbsource());
            GenSingleModel genSingleModel = genModel.getGenSingleModel();
            if (sysDbsourceResult == null) {
                throw new BusinessException(BusinessErrorEnum.DARASOURCE_NOT);
            }
            if (genSingleModel == null || StringUtils.isEmpty(genSingleModel.getTemplate())) {
                throw new BusinessException(BusinessErrorEnum.DATA_NOT_EXIT);
            }

            List<String> validPermissions = new ArrayList<>();
            validPermissions.add(sysNamespace.getPermission());
            validPermissions.add(sysDbsourceResult.getPermission());

            userAuthService.auth(userId, validPermissions);
            threadLocalProperties.set(DynamicDataSourceAspect.THREAD_DB_NAME, sysDbsourceResult.getKeyName());
            threadLocalProperties.set(DbsourceUtils.DBSOURCE_ID, sysDbsourceResult.getId());
            threadLocalProperties.set(NamespaceUtils.NAMESPACE_ID, sysNamespace.getId());
            GenTemplateQuery genTemplateQuery = new GenTemplateQuery();
            genTemplateQuery.setSysNamespaceId(Integer.valueOf(sysNamespace.getId()));
            String[] template = genSingleModel.getTemplate().split(SplitCharEnum.COLON.getName());
            genTemplateQuery.setTemplateName(template[0]);
            if (template.length > 1) {
                genTemplateQuery.setVersion(template[1]);
            }
            GenTemplatePO genTemplatePO = new GenTemplatePO(genTemplateQuery);
            Response<List<GenTemplateResult>> genTemplateResultListRes = genTemplateService.findList(genTemplatePO);
            if (CollectionUtils.isNotEmpty(genTemplateResultListRes.getData())) {
                GenTemplateResult genTemplateResult = genTemplateResultListRes.getData().get(0);
                List<GenParams> paramsList = new ArrayList<>();

                List<TemplateField> templateFieldList = genSingleModel.getFields();
                GenFieldColumnQuery genFieldColumnQuery = new GenFieldColumnQuery();
                genFieldColumnQuery.setGenTemplateId(Integer.valueOf(genTemplateResult.getId()));
                GenFieldColumnPO genFieldColumnPO = new GenFieldColumnPO(genFieldColumnQuery);
                Response<List<GenFieldColumnResult>> genFieldColumnResultListRes = genFieldColumnService.findList(genFieldColumnPO);
                Map<String, String> fieldMap = new HashMap<>(12);
                if (CollectionUtils.isNotEmpty(genFieldColumnResultListRes.getData())) {
                    genFieldColumnResultListRes.getData().forEach(genFieldColumnResult -> {
                        fieldMap.put(genFieldColumnResult.getFieldColumnName(), genFieldColumnResult.getDefaultValue());
                    });
                }
                if (CollectionUtils.isNotEmpty(templateFieldList)) {
                    templateFieldList.forEach(templateField -> {
                        fieldMap.put(templateField.getName(), templateField.getValue());
                    });
                }
                fieldMap.forEach((key, value) -> {
                    GenParams params = new GenParams();
                    params.setFieldColumnName(key);
                    params.setValue(value);
                    paramsList.add(params);
                });

                String tableName = null;
                if (genSingleModel.getTable() != null && StringUtils.isNotEmpty(genSingleModel.getTable().getName())) {
                    tableName = genSingleModel.getTable().getName();
                }
                String sourcePath = TemplateUtils.getUnzipPath(genTemplateResult.getFileBasePath(), sysNamespace.getNamespaceName());
                String outPath = genTemplateFacade.build(paramsList, tableName, sourcePath, genTemplateResult.getLanguage(), sysNamespace, new SysUser(userId), genTemplateResult.getId(),
                        sysOsgiPluginService.findFilePathByBoundleType(BoundleEnum.GEN_HANDLER_PLUGIN_BOUNDLE).getData());
                String uuid = IdGenUtils.randomUUID(RANDOM_MAX);
                RedisCacheUtils.setSysCache(CACHE_CODE_PREFIX + uuid, outPath);
                return new Response<>(uuid);
            }
            throw new BusinessException(BusinessErrorEnum.DATA_NOT_EXIT);
        }
        throw new BusinessException(BusinessErrorEnum.NAMESPACE_NOT);
    }

    @GetMapping(value = { "download/{uuid}" })
    @ApiOperation(value = "下载", notes = "下载模版")
    public void download(@PathVariable String uuid, HttpServletResponse response) {
        String path = RedisCacheUtils.getSysCache(CACHE_TMPL_PREFIX + uuid);
        if (StringUtils.isNotEmpty(path)) {
            DownloadUtils.downloadFile(response, path);
        }
        return;
    }

    @GetMapping(value = { "download/code/{uuid}" })
    @ApiOperation(value = "下载", notes = "下载生成代码")
    public void downloadCode(@PathVariable String uuid, HttpServletResponse response) {
        String path = RedisCacheUtils.getSysCache(CACHE_CODE_PREFIX + uuid);
        if (StringUtils.isNotEmpty(path)) {
            DownloadUtils.downloadFile(response, path);
        }
        return;
    }

    @PostMapping(value = { "push" })
    @ApiOperation(value = "上传", notes = "上传模版文件")
    public Response<GenTemplateResult> upload(@RequestParam("file") MultipartFile file, TemplateInput templateInput) {
        AuthModel authModel = new AuthModel();
        authModel.setUsername(templateInput.getUsername());
        authModel.setPassword(templateInput.getPassword());
        Response<SysUserResult> sysUserResultRes = userAuthService.auth(authModel);
        SysNamespace sysNamespace = sysNamespaceService.get(sysUserResultRes.getData().getId(), templateInput.getNamespace());
        if (sysNamespace != null) {
            userAuthService.authPermission(sysUserResultRes.getData().getId(), sysNamespace.getPermission());
            String result = TemplateUtils.upload(file, sysNamespace);
            if (StringUtils.isNotEmpty(result)) {
                String version = templateInput.getVersion();
                Integer language = DEFAULT_LANGUAGE;
                if (StringUtils.isEmpty(version)) {
                    version = DEFAULT_VERSION;
                }
                if (StringUtils.isNotEmpty(templateInput.getLanguage())) {
                    language = LanguageEnum.getCode(templateInput.getLanguage());
                    if (language == null) {
                        throw new BusinessException(BusinessErrorEnum.PLUGIN_LANGUAGE_NOT_EXIST);
                    }
                }
                String fileName = file.getOriginalFilename();
                String templateName = fileName.split("\\" + SplitCharEnum.DOT.getName())[0];
                GenTemplateQuery genTemplateQuery = new GenTemplateQuery();
                genTemplateQuery.setVersion(version);
                genTemplateQuery.setTemplateName(templateName);
                genTemplateQuery.setSysNamespaceId(Integer.valueOf(sysNamespace.getId()));
                GenTemplatePO genTemplatePO = new GenTemplatePO(genTemplateQuery);
                List<GenTemplateResult> genTemplateResults = genTemplateService.findList(genTemplatePO).getData();
                GenTemplate genTemplate = new GenTemplate();
                if (CollectionUtils.isNotEmpty(genTemplateResults)) {
                    genTemplate.setId(genTemplateResults.get(0).getId());
                }
                if (StringUtils.isNotEmpty(templateInput.getField())) {
                    try {
                        List<GenFieldColumn> genFieldColumns = JsonMapper.getInstance().readValue(templateInput.getField(), new TypeReference<List<GenFieldColumn>>() {
                        });
                        genTemplate.setGenFieldColumn(genFieldColumns);
                    } catch (Exception e) {
                        throw new BusinessException(SysErrorEnum.FIELD_ERROR);
                    }
                }
                genTemplate.setFileBasePath(result);
                genTemplate.setLanguage(language);
                genTemplate.setDescription(templateInput.getDescription());
                genTemplate.setVersion(version);
                genTemplate.setTags(templateInput.getTags());
                genTemplate.setTemplateName(templateName);
                genTemplate.setSysNamespaceId(Integer.valueOf(sysNamespace.getId()));
                genTemplate.setCreateBy(sysUserResultRes.getData().getId());
                return genTemplateFacade.save(genTemplate);
            }
        }
        throw new BusinessException(BusinessErrorEnum.NAMESPACE_NOT);
    }

}
