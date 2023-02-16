package cn.lienoo.exoplayerextend;/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static com.google.android.exoplayer2.extractor.ts.TsPayloadReader.FLAG_DATA_ALIGNMENT_INDICATOR;
import static com.google.android.exoplayer2.metadata.id3.Id3Decoder.ID3_HEADER_LENGTH;
import static com.google.android.exoplayer2.metadata.id3.Id3Decoder.ID3_TAG;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.Ac3Util;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.ts.H264Reader;
import com.google.android.exoplayer2.extractor.ts.SeiReader;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;

/** Extracts data from (E-)AC-3 bitstreams. */
public final class H264Extractor implements Extractor {

    /** Factory for {@link H264Extractor} instances. */
    public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new H264Extractor()};

    /**
     * The maximum number of bytes to search when sniffing, excluding ID3 information, before giving
     * up.
     */
    private static final int MAX_SNIFF_BYTES = 8 * 1024;
    private static final int MAX_SYNC_FRAME_SIZE = 2786;

    private final ExtendH264Reader reader;
    private final ParsableByteArray sampleData;

    private boolean startedPacket;

    private long firstSampleTimestampUs;
    private static long sampleTime = 10000;

    /** Creates a new extractor for AC-3 bitstreams. */
    public H264Extractor() {
        reader = new ExtendH264Reader( false, true);
        sampleData = new ParsableByteArray(MAX_SYNC_FRAME_SIZE);
    }

    // Extractor implementation.
    @Override
    public boolean sniff(ExtractorInput input) throws IOException {

        return true;
    }

    @Override
    public void init(ExtractorOutput output) {
        reader.createTracks(output, new TrackIdGenerator(0, 1));
        output.endTracks();
        output.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
    }

    @Override
    public void seek(long position, long timeUs) {
        startedPacket = false;
        reader.seek();
    }

    @Override
    public void release() {
        // Do nothing.
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
        int bytesRead = input.read(sampleData.getData(), 0, MAX_SYNC_FRAME_SIZE);
        if (bytesRead == C.RESULT_END_OF_INPUT) {
            return RESULT_END_OF_INPUT;
        }

        // Feed whatever data we have to the reader, regardless of whether the read finished or not.
        sampleData.setPosition(0);
        sampleData.setLimit(bytesRead);

        if (!startedPacket) {
            // Pass data to the reader as though it's contained within a single infinitely long packet.
            reader.packetStarted (/* pesTimeUs= */ firstSampleTimestampUs, FLAG_DATA_ALIGNMENT_INDICATOR);
            startedPacket = true;
        }
        firstSampleTimestampUs+=sampleTime;
        reader.packetStarted(firstSampleTimestampUs, FLAG_DATA_ALIGNMENT_INDICATOR);
        // TODO: Make it possible for the reader to consume the dataSource directly, so that it becomes
        // unnecessary to copy the data through packetBuffer.
        reader.consume(sampleData);
        return RESULT_CONTINUE;
    }
}
