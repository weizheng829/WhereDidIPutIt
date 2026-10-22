package com.nawle.app;

/**
 * 作者与联系信息：替换真实二维码时只改 drawable-nodpi 下的图片。
 */
public final class About {
    private About() {}

    public static final String AUTHOR = "weizheng";
    public static final String EMAIL = "weizheng111@gmail.com";
    public static final String LICENSE_NAME = "MIT";
    public static final String COPYRIGHT = "Copyright © 2026 " + AUTHOR + " · " + LICENSE_NAME + " License";

    public static String versionName(android.content.Context ctx) {
        try {
            return ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }
}
