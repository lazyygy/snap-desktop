package org.esa.snap.remote.execution.local.folder;

import org.esa.snap.ui.loading.LoadingIndicator;

import java.io.IOException;

/**
 * Created by jcoravu on 1/3/2019.
 */
public interface IMountLocalSharedFolderResult {

    public AbstractLocalSharedFolder getLocalSharedDrive();

    public void unmountLocalSharedFolderAsync(LoadingIndicator loadingIndicator, int threadId, IUnmountLocalSharedFolderCallback callback);

    public void unmountLocalSharedFolder(String currentLocalSharedFolderPath, String currentLocalPassword) throws IOException;
}
