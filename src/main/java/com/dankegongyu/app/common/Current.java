package com.dankegongyu.app.common;

import com.alibaba.fastjson.parser.ParserConfig;
import com.dankegongyu.app.common.exception.BaseException;
import com.dankegongyu.app.common.exception.NeedEmailException;
import com.dankegongyu.app.common.exception.NeedLoginException;
import com.dankegongyu.app.common.exception.ParameterException;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;

//@WebFilter(filterName = "current", urlPatterns = {"/*"})
public class Current implements Filter, ApplicationContextAware {
    private static ThreadLocal controllerContext = new ThreadLocal();
    private static final Logger logger = LoggerFactory.getLogger(Current.class);
    public Map<String, Object> context;

    public static final String REQUEST = "javax.servlet.http.HttpServletRequest";
    private static final String RESPONSE = "javax.servlet.http.HttpServletResponse";
    private static final String SESSION = "javax.servlet.http.HttpSession";
    private static final String COOKIE = "javax.servlet.http.HttpCookie";
    private static final String APPLICATION = "javax.faces.application.Application";
    private static final String USERNAME = "current.userName";
    private static final String UUID = "ssid";//SESSION
    private static ServletContext servletContext;
    private static ApplicationContext appContext = null;
    public static final String SERVERIP = Current.getNewLocalIP();  //当前服务IP
    private static String errorPage = "/error";
    private static List<String> excludes = new ArrayList<>();
    private static boolean isApi = false;
    private static boolean isDealError = true;

    public Current() {
    }

    @Autowired
    public void setApplicationContext(ApplicationContext paramApplicationContext) {
        Current.appContext = paramApplicationContext;
    }

    private Current(Map<String, Object> _context) {
        context = _context;
    }

    private static Current getContext() {
        Current context = (Current) controllerContext.get();
        if (null == context) {
            context = new Current(new HashMap<String, Object>());
            controllerContext.set(context);
        }
        return context;
    }

    public static void setContext(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> map = getContext().context;
        map.put(REQUEST, request);
        map.put(RESPONSE, response);
    }

    private static void remove() {
        controllerContext.remove();
        CurrentContext.clear();
    }


    public static <T> T get(String key) {
        return (T) getContext().context.get(key);
    }

    public static void set(String key, Object value) {
        getContext().context.put(key, value);
    }

    public static void resetContext(Map<String, Object> map) {
        CurrentContext.setContext(map);
    }

    public static String getUUID() {
        String uuid = getCookieValue(UUID);
        if (Strings.isNullOrEmpty(uuid) && getRequest() != null)
            uuid = getRequest().getParameter(UUID);
        if (Strings.isNullOrEmpty(uuid) && !Strings.isNullOrEmpty(get(UUID)))
            uuid = get(UUID);
        if (Strings.isNullOrEmpty(uuid)) {
            uuid = UUID19.randomUUID();
            set(UUID, uuid);
        }
        setCookie(UUID, uuid, "/", 12960000);
        return uuid;
    }

    public static String getSessionId() {
        return getUUID();
    }

    public static HttpServletRequest getRequest() {
        Map<String, Object> map = getContext().context;
        if (map.containsKey(REQUEST)) return (HttpServletRequest) map.get(REQUEST);
        return null;
    }

    public static HttpServletResponse getResponse() {
        Map<String, Object> map = getContext().context;
        if (map.containsKey(RESPONSE)) return (HttpServletResponse) map.get(RESPONSE);
        return null;
    }


    public static HttpSession getSession() {
        return getSession(true);
    }

    public static HttpSession getSession(boolean isCreate) {
        if (getRequest() != null)
            return getRequest().getSession(isCreate);
        return null;
    }

    public static <T> T getSession(String key) {
        if (CurrentContext.get("session_" + key) != null) {
            return CurrentContext.get("session_" + key);
        }
        if (getSession() == null) return null;
        Object ret = getSession().getAttribute(key);
        if (ret == null) return null;
        CurrentContext.set("session_" + key, ret);
        return (T) ret;
    }

    public static void setSession(String key, Object value) {
        CurrentContext.set("session_" + key, value);
        if (getSession() == null) return;
        getSession().setAttribute(key, value);
    }

    public static void removeSession(String key) {
        if (getSession() == null) return;
        getSession().removeAttribute(key);
    }

    public static String getCurrentUrl() {
        HttpServletRequest request = Current.getRequest();
        String url = request.getRequestURL().toString();
        if (request.getQueryString() != null)
            url = url + "?" + request.getQueryString();
        try {
            String serviceUrl = URLEncoder.encode(url, "utf-8");
            return serviceUrl;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return url;
    }

    public static Map<String, Cookie> getCookie() {
        Map<String, Cookie> map = get(COOKIE);
        if (map == null && getRequest() != null) {
            map = new HashMap<String, Cookie>();
            HttpServletRequest request = getRequest();
            if (request != null) {
                Cookie[] cookies = request.getCookies();
                if (!(cookies == null || cookies.length == 0))
                    for (Cookie cookie : cookies) {
                        try {
                            cookie.setValue(URLDecoder.decode(cookie.getValue(), "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        map.put(cookie.getName(), cookie);
                    }
            }
            set(COOKIE, map);
        }
        return map;
    }

    public static Cookie getCookie(String key) {
        Map<String, Cookie> map = getCookie();
        if (map != null && map.containsKey(key))
            return map.get(key);
        return null;
    }

    public static String getCookieValue(String key) {
        Cookie cookie = getCookie(key);
        if (cookie != null) return cookie.getValue();
        return "";
    }

    public static void setCookie(String name, String value) {
        String path = "/";
        if (getRequest() != null && !Strings.isNullOrEmpty(getRequest().getContextPath()))
            path = getRequest().getContextPath();
        setCookie(name, value, path);
    }

    public static void setCookie(String name, String value, String path) {
        setCookie(name, value, path, null);
    }

    public static void setCookie(String name, String value, String path, Integer maxAge) {
        try {
            if (!Strings.isNullOrEmpty(value)) value = URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (getResponse() == null) return;
        Cookie cookie = new Cookie(name, value);
        if (maxAge != null)
            cookie.setMaxAge(maxAge);
        cookie.setPath(path);

        Map<String, Cookie> map = getCookie();
        if (!map.containsKey(name) || !map.get(name).getValue().equals(value)) {
            getResponse().addCookie(cookie);
            map.put(name, cookie);
            logger.info(StringFormat.format("[cookie][name]:{0},[value]:{1},[path]:{2},[maxAge]:{3}", name, value, path, maxAge));
        }
        set(COOKIE, map);
    }

    public static void removeCookie(String key) {
        removeCookie(key, "/");
    }

    public static void removeCookie(String key, String path) {
        Cookie cookie = new Cookie(key, "");
        cookie.setMaxAge(0);
        cookie.setPath(path);
        getResponse().addCookie(cookie);
    }


    public static ServletContext getServletContext() {
        return Current.servletContext;
    }

    public static <T> T getModel(Class<T> clazz) {
        Map map = getRequest().getParameterMap();
        return JsonUtils.convert(map, clazz);
    }

    //兼容历史接口,属于过期接口，可以使用Current.SERVERIP
    @Deprecated
    public static String getLocalIP() {
        if (Current.SERVERIP == null || "".equals(Current.SERVERIP)) {
            return getNewLocalIP();
        }
        return Current.SERVERIP;
    }

    ;

    public static String getNewLocalIP() {
        if (get("Current.getLocalIP") == null) {
            Enumeration allNetInterfaces = null;
            InetAddress ip = null;
            List<String> ips = Lists.newArrayList();
            String _ip;
            try {
                allNetInterfaces = NetworkInterface.getNetworkInterfaces();
                while (allNetInterfaces.hasMoreElements()) {
                    NetworkInterface netInterface = (NetworkInterface) allNetInterfaces.nextElement();
                    Enumeration addresses = netInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        ip = (InetAddress) addresses.nextElement();
                        if (ip != null && ip instanceof Inet4Address) {
                            _ip = ip.getHostAddress();
                            if (!_ip.equals("127.0.0.1")) {
                                if (ips.size() == 0)
                                    ips.add(ip.getHostName());
                                ips.add(_ip);
                            }
                        }
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            }
            set("Current.getLocalIP", Joiner.on(",").join(ips));
            logger.info("当前系统ip地址是：" + Joiner.on(",").join(ips));
        }
        return get("Current.getLocalIP");
    }


    public static String getRemortIP() {
        String ip = "";
        HttpServletRequest request = getRequest();
        if (request != null) {
            ip = request.getHeader("x-forwarded-for");
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
        }
        return ip;
    }


    //获取applicationContext
    public static ApplicationContext getApplicationContext() {
        if (Current.appContext == null)
            Current.appContext = WebApplicationContextUtils.getWebApplicationContext(getServletContext());
        return Current.appContext;
    }

    //通过name获取 Bean.
    public static Object getBean(String name) {
        return getApplicationContext().getBean(name);
    }


    //通过class获取Bean.
    public static <T> T getBean(Class<T> clazz) {
        return getApplicationContext().getBean(clazz);
    }

    //通过name,以及Clazz返回指定的Bean
    public static <T> T getBean(String name, Class<T> clazz) {
        return getApplicationContext().getBean(name, clazz);
    }

    public static boolean isAjax() {
        if (getRequest() == null) return false;
        if (Current.get("tojson") != null) return true;
        if (getRequest().getRequestURI().endsWith(".json")) return true;
        String biaozhi = getRequest().getHeader("X-Requested-With");
        return !Strings.isNullOrEmpty(biaozhi) && "XMLHttpRequest".equals(biaozhi);
    }


    public void init(FilterConfig filterConfig) throws ServletException {
        ParserConfig.getGlobalInstance().setAutoTypeSupport(true);
        logger.info(this.getClass().getSimpleName() + " init");
        servletContext = filterConfig.getServletContext();
        String errpath = filterConfig.getInitParameter(errorPage);
        if (!Strings.isNullOrEmpty(errpath))
            errorPage = errpath;
        String exclude = filterConfig.getInitParameter("exclude");
        if (!Strings.isNullOrEmpty(exclude))
            excludes = Splitter.on(";").splitToList(exclude);
        if (!Strings.isNullOrEmpty(filterConfig.getInitParameter("isApi"))) {
            isApi = true;
        }
        if (!Strings.isNullOrEmpty(filterConfig.getInitParameter("isDealError"))) {
            isDealError = false;
        }
    }

    public static void setTraceId() {
        TraceIdUtils.setTraceId();
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (isApi) {
            Current.set("tojson", true);
        }
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        req.setCharacterEncoding("UTF-8");
        HttpServletResponse res = (HttpServletResponse) servletResponse;
        setContext(req, res);
        CurrentContext.initFromSession();
        Long start = new Date().getTime();
        String url = req.getRequestURI();
        try {
//            MDC.put("traceId", UUID19.randomUUID());
            String traceId = req.getParameter("traceId");
            if (traceId == null || traceId.equals(""))
                TraceIdUtils.setTraceId();
            else
                TraceIdUtils.setTraceId(traceId);
            if (isNeedLog(url)) {
                logger.info(getRequestOtherInfo());
            }
            filterChain.doFilter(getRequest(), getResponse());
        } catch (Exception e) {
            if (!isDealError)
                throw e;
//            logger.error(e.getMessage(), e);
            ApiResult result = GlobalExceptionHandler.getErrorResult(e);
            try {
                if (Current.isAjax()) {
                    BaseController.writeJsonToClient(result);
                    res.getOutputStream().write(Current.getRequest().getAttribute(BaseController.MODEL).toString().getBytes(Charsets.UTF_8));
                } else {
                    Current.setSession(GlobalExceptionHandler.currentSessionError, e.getMessage());
                    req.getRequestDispatcher(errorPage).forward(servletRequest, servletResponse);
                }
            } catch (IOException ioe) {
                logger.error(ioe.getMessage(), ioe);
            } catch (ServletException e1) {
                logger.error(e1.getMessage(), e1);
            }

        } finally {
//            if (isNeedLog(url)) {
//                logger.info("[耗时]：{} : {}", req.getRequestURI(), new Date().getTime() - start);
//            }
            MDC.clear();
            remove();
        }
    }


    public void destroy() {
//        remove();
    }

    public static String getRequestOtherInfo() {
        List<String> msg = new ArrayList<String>();
        msg.add("=======================================");
        HttpServletRequest request = Current.getRequest();
        if (request != null) {
            msg.add("当前地址为 : " + request.getRequestURL().toString());
            msg.add("用户IP : " + Current.getRemortIP());

            HttpServletRequest req = (HttpServletRequest) Current.getRequest();
            msg.add("参数信息为 : ");
            msg.add(JsonUtils.toJson(req.getParameterMap()));
            try {
                msg.add(JsonUtils.toJson(FormFilter.getParametersCanJson()));
            } catch (Exception e) {

            }
            msg.add("header信息为 : ");
            msg.add(JsonUtils.toJson(getHeaderInfo()));
            msg.add("cookie信息为 : ");
            msg.add(JsonUtils.toJson(Current.getCookie()));
        }
        msg.add("SERVER IP : " + Current.SERVERIP);
        if (AppUtils.getBean(Environment.class) != null) {
            Environment environment = AppUtils.getBean(Environment.class);
            String appName = environment.getProperty("spring.application.name");
            if (!Strings.isNullOrEmpty(appName)) {
                msg.add("AppName : " + appName);
            }
        }
        msg.add("=======================================");
        msg.add("");
        return StringUtils.join(msg, " <br />");
    }


    private static Map getHeaderInfo() {
        Map map = new HashMap();
        HttpServletRequest request = Current.getRequest();
        Enumeration enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            String key = enumeration.nextElement().toString();
            if (!key.equals("cookie"))
                map.put(key, request.getHeader(key));
        }
        return map;
    }

    public static void sendErrorMsg(Throwable ex) {
        String subject = "【报警】" + ex.getMessage() + ": " + MDC.get("traceId") + ":" + SERVERIP;
        String msg = subject + "<br />" + Current.getRequestOtherInfo();
        Mailer mailer = Mailer.getMailer();
        StringWriter stringWriter = new StringWriter();
        ex.printStackTrace(new PrintWriter(stringWriter));
        msg = msg + "<br />" + stringWriter.toString();
        GlobalExceptionHandler.setCurrentThreadError(msg);
        if ((!(ex instanceof BaseException) || ex instanceof NeedEmailException) && mailer != null)
            mailer.sendMail(subject, msg);
    }

    public static boolean isNeedLog(String url) {
        for (String exclude : excludes) {
            if (url.matches(exclude))
                return false;
        }
        return true;
    }

}