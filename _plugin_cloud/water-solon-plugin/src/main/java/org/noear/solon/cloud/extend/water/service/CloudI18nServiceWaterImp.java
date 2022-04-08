package org.noear.solon.cloud.extend.water.service;

import org.noear.solon.cloud.model.Pack;
import org.noear.solon.cloud.model.PackHolder;
import org.noear.solon.cloud.service.CloudI18nService;
import org.noear.solon.core.event.EventBus;
import org.noear.water.WaterClient;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author noear
 * @since 1.6
 */
public class CloudI18nServiceWaterImp implements CloudI18nService {
    Map<String, PackHolder> packHolderMap = new ConcurrentHashMap<>();

    @Override
    public Pack pull(String group, String bundleName, Locale locale) {
        try {
            return pullDo(group, bundleName, locale).getPack();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public PackHolder pullDo(String group, String bundleName, Locale locale) throws IOException {
        String packKey = String.format("%s:%s:%s", group, bundleName, locale.toString());

        PackHolder packHolder = packHolderMap.get(packKey);

        if (packHolder == null) {
            synchronized (packKey.intern()) {
                packHolder = packHolderMap.get(packKey);

                if (packHolder == null) {
                    packHolder = new PackHolder(group, bundleName, locale);
                    Map<String, String> data = WaterClient.I18n.getI18n(group, bundleName, packHolder.getLang());
                    packHolder.getPack().setData(data);
                }

                packHolderMap.put(packKey, packHolder);
            }
        }

        return packHolder;
    }

    public void onUpdate(String group, String bundleName, String lang) {
        String packKey = String.format("%s:%s:%s", group, bundleName, lang);

        PackHolder packHolder = packHolderMap.get(packKey);
        if (packHolder != null) {
            try {
                Map<String, String> data = WaterClient.I18n.getI18n(group, bundleName, packHolder.getLang());
                packHolder.getPack().setData(data);
            } catch (Throwable e) {
                EventBus.push(e);
            }
        }
    }
}
