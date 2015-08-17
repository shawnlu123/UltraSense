package jakobkarolus.de.ultrasense.view;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;

import jakobkarolus.de.ultrasense.R;
import jakobkarolus.de.ultrasense.algorithm.AlgoHelper;
import jakobkarolus.de.ultrasense.audio.CWSignalGenerator;
import jakobkarolus.de.ultrasense.audio.SignalGenerator;

/**
 * <br><br>
 * Created by Jakob on 17.08.2015.
 */
public class CalibrationFragment extends Fragment{

    private static final int SAMPLE_RATE = 44100;
    private static final int CALIBRATION_DURATION = 5;
    private static final int FFT_LENGTH = 4096;
    private static final int FFT_HOP_SIZE = 2048;
    private static final double CARRIER_FREQ = 20000;
    private static final int minSize = CALIBRATION_DURATION*SAMPLE_RATE*2;//4*4096;
    private static final double CARRIER_IDX = ((CARRIER_FREQ / ((double) SAMPLE_RATE / 2.0)) * (FFT_LENGTH / 2 + 1)) - 1;
    private static int BITMAP_HEIGHT=201;
    private static int BITMAP_HEIGHT_FACTOR = 2;

    private Button startCalib;
    private Button updateCalib;
    private TouchImageView imageView;
    private LinearLayout calibView;
    private NumberPicker npThreshold;
    private NumberPicker npHalfCarrierWidth;
    private NumberPicker npFeatureMin;
    private NumberPicker npFeatureMax;

    private SignalGenerator signalGenerator;
    private double[][] spectrogram;
    private boolean recordRunning=false;

    private int currentHalfCarrierWidth = 4;
    private double currentMagnitudeThreshold = -60.0;
    private int currentFeatureMin = 1;
    private int currentFeatureMax = 3;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        signalGenerator = new CWSignalGenerator(CARRIER_FREQ, SAMPLE_RATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_calibration, container, false);
        startCalib = (Button) rootView.findViewById(R.id.button_record_calibration);
        imageView = (TouchImageView) rootView.findViewById(R.id.calib_spectrogram);
        calibView = (LinearLayout) rootView.findViewById(R.id.calib_view);
        npThreshold = (NumberPicker) rootView.findViewById(R.id.calib_threshold);
        npHalfCarrierWidth = (NumberPicker) rootView.findViewById(R.id.calib_halfCarrierWidth);
        npFeatureMin = (NumberPicker) rootView.findViewById(R.id.calib_feature_min);
        npFeatureMax = (NumberPicker) rootView.findViewById(R.id.calib_feature_max);

        updateCalib = (Button) rootView.findViewById(R.id.button_update_calib);
        startCalib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordCalibration();
            }
        });
        updateCalib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentHalfCarrierWidth = npHalfCarrierWidth.getValue();
                currentMagnitudeThreshold = npThreshold.getValue() * -1;
                currentFeatureMin = npFeatureMin.getValue();
                currentFeatureMax = npFeatureMax.getValue();
                imageView.setImageBitmap(extractValues());
            }
        });

        npThreshold.setMinValue(50);
        npThreshold.setMaxValue(60);
        npThreshold.setWrapSelectorWheel(false);
        npThreshold.setValue((int) currentMagnitudeThreshold * (-1));
        npThreshold.setFormatter(new NumberPicker.Formatter() {
            @Override
            public String format(int value) {
                return Integer.toString(value * -1);
            }
        });

        npHalfCarrierWidth.setMinValue(2);
        npHalfCarrierWidth.setMaxValue(8);
        npHalfCarrierWidth.setValue((int) currentHalfCarrierWidth);
        npHalfCarrierWidth.setWrapSelectorWheel(false);

        npFeatureMin.setMinValue(0);
        npFeatureMin.setMaxValue(8);
        npFeatureMin.setValue((int) currentFeatureMin);
        npFeatureMin.setWrapSelectorWheel(false);

        npFeatureMax.setMinValue(0);
        npFeatureMax.setMaxValue(8);
        npFeatureMax.setValue((int) currentFeatureMax);
        npFeatureMax.setWrapSelectorWheel(false);

        return rootView;
    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Feature Detection Calibration");
        builder.setMessage(R.string.calib_message);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.show();

    }

    private void recordCalibration() {

        new RecordCalibrationAudioTask().execute();

    }

    private class RecordCalibrationAudioTask extends AsyncTask<Void, String, Bitmap>{

        private ProgressDialog pd;

        @Override
        protected void onPreExecute() {
            pd = ProgressDialog.show(getActivity(), "Recording Audio", "Please wait", true, false);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            pd.setMessage(values[0]);
        }

        @Override
        protected void onPostExecute(Bitmap image) {
            pd.dismiss();

            imageView.setImageBitmap(image);
            calibView.setVisibility(View.VISIBLE);
            startCalib.setVisibility(View.GONE);

        }


        @Override
        protected Bitmap doInBackground(Void... params) {
            final AudioTrack at = new AudioTrack(android.media.AudioManager.STREAM_MUSIC,SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,4*4096,AudioTrack.MODE_STREAM);
            final AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 10*4*4096);

            publishProgress("Generating signal");
            final byte[] audio = signalGenerator.generateAudio();
            publishProgress("Recording! Perform any gesture.");

            Thread playThread = new Thread(new Runnable() {
                @Override
                public void run() {

                    at.play();
                    while(recordRunning){
                        at.write(audio, 0, audio.length);
                    }
                    at.stop();
                    at.release();
                }
            });

            recordRunning = true;
            playThread.start();

            ar.startRecording();
            byte[] buffer = new byte[CALIBRATION_DURATION*SAMPLE_RATE*2];
            int overallSamplesRead = 0;
            while(overallSamplesRead < buffer.length) {
                int samplesRead = ar.read(buffer, overallSamplesRead, Math.min(4*4096, buffer.length-overallSamplesRead));
                overallSamplesRead+=samplesRead;
            }
            ar.stop();
            ar.release();
            recordRunning=false;

            publishProgress("Converting audio");
            double[] bufferDouble = convertToDouble(buffer, buffer.length);
            //double[] bufferDouble = convertToDouble(audio, audio.length);
            publishProgress("Performing FFT");
            updateSpectrogram(bufferDouble);
            publishProgress("Extracting values and creating bitmap");
            Bitmap image = extractValues();
            return image;

            }
        }

    private void updateSpectrogram(double[] audioBuffer) {

        spectrogram = new double[audioBuffer.length/FFT_HOP_SIZE-1][(FFT_LENGTH / 2 + 1)];
        AlgoHelper.applyHighPassFilter(audioBuffer);
        double[] win = AlgoHelper.getHannWindow(FFT_LENGTH);
        double windowAmp = AlgoHelper.sumWindowNorm(win);

        double[] buffer = new double[FFT_LENGTH];
        int counter=0;
        for (int i = 0; i <= audioBuffer.length - FFT_LENGTH; i += FFT_HOP_SIZE) {
            System.arraycopy(audioBuffer, i, buffer, 0, FFT_LENGTH);

            spectrogram[counter] = AlgoHelper.fftMagnitude(buffer, win, windowAmp);
            counter++;
        }
    }

    private Bitmap extractValues() {
        int[][] image = new int[spectrogram.length][BITMAP_HEIGHT];
        for(int i=0; i < spectrogram.length; i++){
            double[] valuePerTimestep = meanExtraction(spectrogram[i], CARRIER_IDX, currentHalfCarrierWidth);
            generateImageColumn(valuePerTimestep[0], valuePerTimestep[1], image[i]);
        }

        int[] imageArray = new int[BITMAP_HEIGHT*spectrogram.length];
        int counter =0;
        for(int i=0; i < BITMAP_HEIGHT; i++){
            for(int j=0; j < image.length; j++){
                if(i==BITMAP_HEIGHT/2)
                    imageArray[counter] = Color.rgb(0, 0, 0);
                else {
                    if (image[j][i] == 0)
                        imageArray[counter] = Color.rgb(255, 255, 255);
                    else
                        imageArray[counter] = image[j][i];
                }
                counter++;
            }
        }

        return Bitmap.createBitmap(imageArray, spectrogram.length, BITMAP_HEIGHT, Bitmap.Config.ARGB_8888);
    }

    private void generateImageColumn(double meanHigh, double meanLow, int[] image) {
        image[Math.min(BITMAP_HEIGHT/2+(int)Math.round(meanHigh*BITMAP_HEIGHT_FACTOR), BITMAP_HEIGHT-1)] = Color.rgb(255, 0, 0);
        image[Math.max(BITMAP_HEIGHT / 2 - (int) Math.round(meanLow * BITMAP_HEIGHT_FACTOR), 0)] = Color.rgb(255, 0, 0);
        image[BITMAP_HEIGHT/2+currentFeatureMin*BITMAP_HEIGHT_FACTOR] = Color.rgb(0, 255, 0);
        image[BITMAP_HEIGHT/2-currentFeatureMin*BITMAP_HEIGHT_FACTOR] = Color.rgb(0, 255, 0);
        image[BITMAP_HEIGHT/2+currentFeatureMax*BITMAP_HEIGHT_FACTOR] = Color.rgb(0, 0, 255);
        image[BITMAP_HEIGHT/2-currentFeatureMax*BITMAP_HEIGHT_FACTOR] = Color.rgb(0, 0, 255);


    }

    private double[] meanExtraction(double[] values, double carrierIdx, int halfCarrierWidth) {
        double[] means = new double[2];

        //high doppler
        double meanWeightsHigh = 0.0;
        double meanHigh = 0.0;
        int offset = (int) Math.ceil(carrierIdx) + halfCarrierWidth;
        for(int i=0; i < (values.length - offset); i++){
            if(values[offset + i] > currentMagnitudeThreshold){
                meanHigh += (i+1)*values[offset + i];
                meanWeightsHigh += values[offset + i];
            }
        }
        if(Math.abs(0.0 - meanWeightsHigh) > 1e-6) {
            meanHigh /= meanWeightsHigh;
        }
        //low doppler
        double meanWeightsLow = 0.0;
        double meanLow = 0.0;
        offset = (int) Math.floor(carrierIdx) - halfCarrierWidth;
        for(int i=0; i <= offset; i++){
            if(values[offset - i] > currentMagnitudeThreshold){
                meanLow += (i+1)*values[offset - i];
                meanWeightsLow += values[offset - i];
            }
        }
        if(Math.abs(0.0 - meanWeightsLow) > 1e-6) {
            meanLow /= meanWeightsLow;
        }

        means[0] = meanHigh;
        means[1] = meanLow;

        /*
        if(ignoreNoise){
            for(int i=0; i< means.length; i++)
                if(means[i] > maxFeatureThreshold)
                    means[i] = 0.0;
        }
        */

        return means;

    }



    private double[] convertToDouble(byte[] buffer, int bytesRead) {

        //from http://stackoverflow.com/questions/5774104/android-audio-fft-to-retrieve-specific-frequency-magnitude-using-audiorecord
        double[] bufferDouble = new double[buffer.length/2];
        final int bytesPerSample = 2; // As it is 16bit PCM
        final double amplification = 1.0; // choose a number as you like
        for (int index = 0, floatIndex = 0; index < bytesRead - bytesPerSample + 1; index += bytesPerSample, floatIndex++) {
            double sample = 0;
            for (int b = 0; b < bytesPerSample; b++) {
                int v = buffer[index + b];
                if (b < bytesPerSample - 1 || bytesPerSample == 1) {
                    v &= 0xFF;
                }
                sample += v << (b * 8);
            }
            double sample32 = amplification * (sample / 32768.0);
            bufferDouble[floatIndex] = sample32;
        }

        return bufferDouble;
    }



}
