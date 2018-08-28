package cn.davidsu.customstickylivedata;

import cn.davidsu.library.CustomStickyLiveData;

/**
 * Created by cxzheng on 2018/8/28.
 */

public class GlobalDataManager {

    private static CustomStickyLiveData<String> test = new CustomStickyLiveData<>();


    public static CustomStickyLiveData<String> getTestLiveData() {
        return test;
    }
}
