package cn.lienoo.exoplayerextend;

import static java.lang.Long.min;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public final class SmbDataSource extends BaseDataSource {

    public static final class SmbDataSourceException extends DataSourceException {
        public SmbDataSourceException(Throwable cause, @PlaybackException.ErrorCode int errorCode) {
            super(cause, errorCode);
        }
    }

    private String userName;
    private String password;
    private String hostName;
    private String shareName;
    private String path;
    private DataSpec dataSpec;

    private SMBClient smbClient = null;
    private InputStream inputStream = null;
    private Long bytesRemaining = 0L;
    private Boolean opened;

    public SmbDataSource(DataSpec theDataSpec) {
        super(true);

        dataSpec = theDataSpec;
        Uri uri = theDataSpec.uri;
        String userInfo = uri.getUserInfo();
        if ( userInfo != null && !userInfo.isEmpty() )
        {
            String[] userInfoArray = userInfo.split(":");
            userName = (userInfoArray[0] != null) ? userInfoArray[0] : "";
            password = (userInfoArray[1] != null) ? userInfoArray[1] : "";
            hostName = (uri.getHost() != null) ? uri.getHost() : "";

            String[] dirPath;
            if (uri.getPath().startsWith("/")) {
                dirPath = uri.getPath().substring(1).split("/", 2);
            } else {
                dirPath = uri.getPath().split("/", 2);
            }
            shareName = dirPath[0];
            path = dirPath[dirPath.length - 1];
        }
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        try {
            smbClient = new SMBClient();
            Connection connection = smbClient.connect(hostName);
            if ( connection.isConnected() ) {
                AuthenticationContext ac = new AuthenticationContext(userName, password.toCharArray(), "WORKGROUP");
                Session session = connection.authenticate(ac);

                // Connect to Share
                DiskShare share = (DiskShare) session.connectShare(shareName);
                if ( share.isConnected() ) {
                    Set<SMB2ShareAccess> shareAccess = new HashSet<SMB2ShareAccess>();
                    shareAccess.add(SMB2ShareAccess.ALL.iterator().next()); // READ only
                    File remoteSmbjFile = share.openFile(path, EnumSet.of(AccessMask.GENERIC_READ),
                            null, shareAccess, SMB2CreateDisposition.FILE_OPEN, null);

                    // Open the input stream
                    inputStream = remoteSmbjFile.getInputStream();

                    // Get the number of bytes that can be read from the opened source
                    long skipped = inputStream.skip(dataSpec.position);
                    if (skipped < dataSpec.position) {
                        throw new IOException();
                    }
                    if (dataSpec.length != C.LENGTH_UNSET) {
                        bytesRemaining = dataSpec.length;
                    } else {
                        bytesRemaining = dataSpec.length;
                        if (bytesRemaining == Integer.MAX_VALUE) {
                            bytesRemaining = Long.valueOf(C.LENGTH_UNSET);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.d("TAG", e.toString());
        }

        return bytesRemaining;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return dataSpec.uri;
    }

    @Override
    public void close() throws IOException {
        try {
            if ( inputStream != null ) inputStream.close();
            if ( smbClient != null ) smbClient.close();
        } catch (IOException e) {
            throw new IOException(e);
        } finally {
            inputStream = null;
            smbClient = null;
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        } else if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        }

        // Here we are going to read the input stream and get the number of bytes read
        int bytesRead = -1;
        try {
            int bytesToRead = -1;
            if (bytesRemaining == C.LENGTH_UNSET) bytesToRead = length; else bytesToRead = (int) min(bytesRemaining, length);
            bytesRead = inputStream.read(buffer, offset, bytesToRead);
        } catch (IOException e) {
            throw new IOException(e);
        }

        if (bytesRead == -1) {
            if (bytesRemaining != C.LENGTH_UNSET) {
                // End of stream reached having not read sufficient data.
                throw new IOException();
            }
            return C.RESULT_END_OF_INPUT;
        }
        if (bytesRemaining != C.LENGTH_UNSET) {
            bytesRemaining -= bytesRead;
        }

        return bytesRead;
    }
}
