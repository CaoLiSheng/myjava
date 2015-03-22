package toonly.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import toonly.appobj.AppFactory;
import toonly.appobj.UnPermissioned;
import toonly.configer.PropsConfiger;
import toonly.configer.cache.UncachedException;
import toonly.dbmanager.base.Jsonable;
import toonly.dbmanager.lowlevel.RS;
import toonly.debugger.Debugger;
import toonly.mapper.ret.RB;
import toonly.mapper.ret.RBArray;
import toonly.repos.ReposManager;
import toonly.wrapper.Bool;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Created by caoyouxin on 15-2-25.
 */
@WebServlet(name = "flag_mapper", urlPatterns = { "/api/v1/*" })
public class FlagMapper extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(FlagMapper.class);

    private ThreadLocal<String> _line1 = new ThreadLocal<>();
    private Properties _redirectConfiger = this.getConfger();

    private Properties getConfger() {
        PropsConfiger propsConfiger = new PropsConfiger();
        try {
            return propsConfiger.cache("redirect.prop");
        } catch (UncachedException e) {
            return propsConfiger.config("redirect.prop");
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if (!ReposManager.getInstance().isUpToDate()) {
            resp.sendRedirect(this._redirectConfiger.getProperty("init_page", "/init.html"));
            return;
        }

        /**
         * 读取数据
         */
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(req.getInputStream()));
        _line1.set(bufferedReader.readLine());
        bufferedReader.close();

        /**
         * 打印调试信息
         */
        Debugger.debugExRun(this, () -> this.printRequest(req));

        /**
         * 解析path
         */
        String[] info = req.getPathInfo().split("\\/");
        Debugger.debugRun(this, () -> log.info("info : {}", Arrays.toString(info)));

        /**
         * 实例化对象
         */
        Object app = AppFactory.instance.getAppObject(info[2]);

        /**
         * 注入值
         */
        boolean constructed = false;
        if (app instanceof Jsonable) {
            Jsonable jsonable = (Jsonable) app;
            constructed = jsonable.fromJson(null == _line1.get() ? "" : _line1.get());
        }
        if (!constructed && app instanceof ParamConstructable) {
            ParamConstructable paramConstructable = (ParamConstructable) app;
            constructed = paramConstructable.construct(req.getParameterMap());
        }

        /**
         * 执行调用
         */
        Object invokeRet = AppFactory.instance.invokeMethod(req.getAttribute("un").toString(), app, info[3]);
        if (null != invokeRet) {
            this.ret(resp, info[1], invokeRet);
            return;
        }
        sendResponse(resp, this._buildRB(false, "app 执行调用返回null", null));
    }

    private void ret(HttpServletResponse resp, String info, Object invokeRet) throws IOException {
        if (null == invokeRet) {
            sendResponse(resp, this._buildRB(false, "app object produced null ret", null));
        } else if (invokeRet instanceof UnPermissioned) {
            sendResponse(resp, this._buildRB(false, "unpermissioned", null));
        }

        switch (info) {
            case "entity":
                if (invokeRet instanceof Boolean)
                    sendResponse(resp, this._buildRB((boolean) invokeRet, null, null));
                else if (invokeRet instanceof RS)
                    sendResponse(resp, this._buildRB((RS) invokeRet));
                else
                    sendResponse(resp, this._buildRB(false, "nonsense", null));
                break;
            case "func":
                if (invokeRet instanceof Boolean)
                    sendResponse(resp, this._buildRB((boolean) invokeRet, null, null));
                else
                    sendResponse(resp, this._buildRB(false, "nonsense", null));
                break;
            default:
                sendResponse(resp, this._buildRB(false, "nonsense", null));
        }
    }

    private RB _buildRB(RS invokeRet) {
        RB ret = new RB();

        if (invokeRet.isEmpty())
            return ret.put("suc", Bool.FALSE.toString());

        RBArray array = new RBArray();
        while (invokeRet.next()) {
            RB rb = new RB();
            invokeRet.forEach((key, value) -> rb.put(key, value.toString()));
            array.add(rb);
        }
        return ret.put("suc", Bool.TRUE.toString()).put("data", array);
    }

    private RB _buildRB(boolean suc, String problem, Exception e) {
        RB ret = new RB();

        if (suc) {
            return ret.put("suc", Bool.TRUE.toString());
        } else if (null != e) {
            return ret.put("suc", Bool.FALSE.toString()).put("problem", String.format("%s %s", problem, e.getMessage()));
        } else {
            return ret.put("suc", Bool.FALSE.toString()).put("problem", problem);
        }
    }

    public static void sendResponse(HttpServletResponse resp, RB ret) throws IOException {
        resp.addHeader("Content-Type", "application/json;charset=UTF-8");
        ServletOutputStream outputStream = resp.getOutputStream();
        outputStream.write(ret.toJson().getBytes("UTF-8"));
        outputStream.flush();
        outputStream.close();
    }

    private void printRequest(HttpServletRequest req) throws IOException {
        req.setCharacterEncoding("UTF-8");

        log.info("servlet path : {}", req.getServletPath());
        log.info("servlet content type : {}", req.getContentType());
        log.info("query string : {}", req.getQueryString());
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String arg = headerNames.nextElement();
            log.info("header[{} : {}]", arg, req.getHeader(arg));
        }

        Cookie[] cookies = req.getCookies();
        if (null != cookies) {
            for (Cookie cookie : cookies) {
                log.info("cookie name : {}", cookie.getName());
                log.info("cookie path : {}", cookie.getPath());
                log.info("cookie domain : {}", cookie.getDomain());
                log.info("cookie value : {}", cookie.getValue());
                log.info("cookie comment : {}", cookie.getComment());
            }
        }

        Enumeration<String> attributeNames = req.getAttributeNames();
        while (attributeNames.hasMoreElements()) {
            String arg = attributeNames.nextElement();
            log.info("attr[{} : {}]", arg, req.getAttribute(arg));
        }

        req.getParameterMap().forEach((parameterName, parameters) ->
            log.info("param[{} :({})]", parameterName, Arrays.toString(parameters))
        );

        log.info("line({}) : {}", 1, _line1.get());
        log.info("==========华丽的分割线==========");
    }

}
