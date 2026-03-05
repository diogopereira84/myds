package com.fedex.automation.utils;

import java.io.File;

public interface TestResourceProvider {
    File loadToTempFile(String resourcePath, String fileSuffix);
    File loadToTempFile(String resourcePath);
}
