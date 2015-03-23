package toonly.configer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import toonly.configer.cache.Cache;
import toonly.configer.cache.CachedConfiger;
import toonly.configer.cache.UncachedException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by caoyouxin on 15-2-23.
 */
public class ReportConfiger implements FileTool, CachedConfiger<ReportConfiger>, SimpleConfiger<ReportConfiger> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportConfiger.class);

    private final Cache<MyList> cache = Cache.get(MyList.class);

    private MyList docs = new MyList();
    private Map<String, String> reps = new HashMap<>();

    private ReportConfiger(MyList cache) {
        this.docs = cache;
    }

    public ReportConfiger() {
    }

    public ReportConfiger report(String key, String value) {
        this.reps.put(key, value);
        return this;
    }

    @Override
    public ReportConfiger config(String relativePath) {
        BufferedReader bufferedReader = null;
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(this.getFile(relativePath))));
        } catch (FileNotFoundException e) {
        }

        if (null == bufferedReader) {
            return null;
        }

        bufferedReader.lines().forEach((line) -> {
            this.docs.addAll(Arrays.asList(line.split("%")));
            this.docs.add("\n");
        });
        cache.store(relativePath, this.docs);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        this.docs.stream()
                .map((doc) -> this.reps.containsKey(doc) ? this.reps.get(doc) : doc)
                .forEach(sb::append);
        return sb.toString();
    }

    @Override
    public ReportConfiger cache(String relativePath) {
        try {
            return new ReportConfiger(cache.cache(relativePath));
        } catch (UncachedException e) {
            LOGGER.info(e.getLocalizedMessage());
            return this.config(relativePath);
        }
    }

    private static class MyList extends ArrayList<String> {
    }
}
