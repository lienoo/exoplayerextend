package cn.lienoo.exoplayerextend;

import android.net.Uri;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;

public class SmbDataSourceFactory implements DataSource.Factory {
    private String fileUrl;

    public SmbDataSourceFactory(String theFileUrl)
    {
        fileUrl = theFileUrl;
    }

    @Override
    public DataSource createDataSource() {
        DataSpec dataSpec = new DataSpec(Uri.parse(fileUrl));
        return new SmbDataSource(dataSpec);
    }
}
