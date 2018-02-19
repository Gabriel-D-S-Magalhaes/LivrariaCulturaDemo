package vivacity.com.br.livrariaculturademo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.qihancloud.opensdk.base.BindBaseActivity;
import com.qihancloud.opensdk.beans.FuncConstant;
import com.qihancloud.opensdk.beans.OperationResult;
import com.qihancloud.opensdk.function.beans.EmotionsType;
import com.qihancloud.opensdk.function.beans.FaceRecognizeBean;
import com.qihancloud.opensdk.function.beans.LED;
import com.qihancloud.opensdk.function.beans.headmotion.AbsoluteAngleHeadMotion;
import com.qihancloud.opensdk.function.beans.wheelmotion.DistanceWheelMotion;
import com.qihancloud.opensdk.function.beans.wheelmotion.RelativeAngleWheelMotion;
import com.qihancloud.opensdk.function.unit.HardWareManager;
import com.qihancloud.opensdk.function.unit.HeadMotionManager;
import com.qihancloud.opensdk.function.unit.MediaManager;
import com.qihancloud.opensdk.function.unit.ProjectorManager;
import com.qihancloud.opensdk.function.unit.SystemManager;
import com.qihancloud.opensdk.function.unit.WheelMotionManager;
import com.qihancloud.opensdk.function.unit.interfaces.media.FaceRecognizeListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import ai.api.AIServiceException;
import ai.api.android.AIConfiguration;
import ai.api.android.AIDataService;
import ai.api.model.AIError;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Result;
import ai.api.ui.AIDialog;
import ai.api.util.StringUtils;

public class MainActivity extends BindBaseActivity implements AIDialog.AIDialogListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String EXTRA_PROJECTOR_ON = "br.com.vivacity.livrariaculturademo.PROJECTOR_ON";

    private AIDialog aiDialog;
    private AIDataService aiDataService;
    private AIRequest aiRequest;

    private static final int REQUEST_ENABLE_BT = 2; // Deve ser maior que 0!

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket bluetoothServerSocket;
    private BluetoothSocket bluetoothSocket;

    // Qihan SDK
    private SystemManager systemManager;
    private HardWareManager hardWareManager;
    private MediaManager mediaManager;
    private HeadMotionManager headMotionManager;
    private WheelMotionManager wheelMotionManager;
    private ProjectorManager projectorManager;

    // Text to Speech usando Mecanismo de conversão de texto em voz do Google
    private TextToSpeech textToSpeech;

    private LinearLayout linearLayoutButton;
    private TextView receivedMessageTextView;

    private boolean projectorOn = false;

    private ArrayList<String> values;

    public ArrayList<String> getValues() {
        return values;
    }

    public void setValues(ArrayList<String> values) {
        this.values = values;
    }

    /**
     * The activity life cycle starts HERE !!!
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep screen on
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        linearLayoutButton = (LinearLayout) findViewById(R.id.linearLayoutButtons);
        receivedMessageTextView = (TextView) findViewById(R.id.receivedMessageTextView);

        systemManager = (SystemManager) getUnitManager(FuncConstant.SYSTEM_MANAGER);
        hardWareManager = (HardWareManager) getUnitManager(FuncConstant.HARDWARE_MANAGER);
        mediaManager = (MediaManager) getUnitManager(FuncConstant.MEDIA_MANAGER);
        headMotionManager = (HeadMotionManager) getUnitManager(FuncConstant.HEADMOTION_MANAGER);
        wheelMotionManager = (WheelMotionManager) getUnitManager(FuncConstant.WHEELMOTION_MANAGER);
        projectorManager = (ProjectorManager) getUnitManager(FuncConstant.PROJECTOR_MANAGER);

        final AIConfiguration config = new AIConfiguration(
                getString(R.string.dialogflow_token),
                AIConfiguration.SupportedLanguages.PortugueseBrazil,
                AIConfiguration.RecognitionEngine.System);

        aiDataService = new AIDataService(getApplicationContext(), config);

        aiDialog = new AIDialog(this, config);
        aiDialog.setResultsListener(this);
        //String text = "Olá Pedro e Sérgio Herz.. É uma honra recebê-los aqui na Livraria Cultura. " + "Eu sou o Sanbot, o mascote digital da família Cultura.. Em que posso te ajudar hoje?";
        //speak(text, TextToSpeech.QUEUE_FLUSH);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(TAG, "onStart triggered");

        setUpBluetooth();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume triggered");

        if (aiDialog != null) {
            aiDialog.resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "onPause triggered");

        if (aiDialog != null) {

            aiDialog.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy triggered");

        if (this.textToSpeech != null) {

            this.textToSpeech.shutdown();
        }

        if (this.bluetoothServerSocket != null) {

            try {

                this.bluetoothServerSocket.close();

            } catch (IOException e) {

                Log.e(TAG, e.getMessage());
            }
        }

        if (this.bluetoothSocket != null) {

            try {

                this.bluetoothSocket.close();

            } catch (IOException e) {

                Log.e(TAG, e.getMessage());
            }
        }
    }

    /**
     * The activity life cycle ends HERE !!!
     */

    @Override
    protected void onMainServiceConnected() {

        /*OperationResult ledOperationResult = controlOfColorfulLight(LED.PART_RIGHT_HAND, LED.MODE_YELLOW);
        if (ledOperationResult.getErrorCode() == 1) {

            Log.i(TAG, "controlOfColorfulLight(part, mode): A instrução é executada com sucesso");

        } else {

            Log.e(TAG, "Result: " + ledOperationResult.getResult()
                    + "\nDescription: " + ledOperationResult.getDescription()
                    + "\nError code: " + ledOperationResult.getErrorCode()
                    + "\nDescribe contents: " + ledOperationResult.describeContents());
        }*/

        //OperationResult faceOperationResult;
        //faceOperationResult = faceEmotionControl(EmotionsType.NORMAL); // Padrão
        //faceOperationResult = faceEmotionControl(EmotionsType.QUESTION); // DÚVIDA
        //faceOperationResult = faceEmotionControl(EmotionsType.SMILE); // Sorriso
        //faceOperationResult = faceEmotionControl(EmotionsType.LAUGHTER); //Apaixonada 1
        //faceOperationResult = faceEmotionControl(EmotionsType.KISS); // Apaixonada 2
        //faceOperationResult = faceEmotionControl(EmotionsType.ABUSE); // Abuso
        //faceOperationResult = faceEmotionControl(EmotionsType.ANGRY); // Raiva
        //faceOperationResult = faceEmotionControl(EmotionsType.ARROGANCE); // ARROGÂNCIA 1
        //faceOperationResult = faceEmotionControl(EmotionsType.PICKNOSE); // ARROGÂNCIA 2
        //faceOperationResult = faceEmotionControl(EmotionsType.CRY); // CHORAR
        //faceOperationResult = faceEmotionControl(EmotionsType.FAINT); // DESMAIAR
        //faceOperationResult = faceEmotionControl(EmotionsType.GOODBYE); // ADEUS
        //faceOperationResult = faceEmotionControl(EmotionsType.GRIEVANCE); // OFENDIDA
        //faceOperationResult = faceEmotionControl(EmotionsType.PRISE); // PRÊMIO
        //faceOperationResult = faceEmotionControl(EmotionsType.SHY); // TÍMIDO
        //faceOperationResult = faceEmotionControl(EmotionsType.SLEEP); // DORMINDO
        //faceOperationResult = faceEmotionControl(EmotionsType.SNICKER); // riso abafado
        //faceOperationResult = faceEmotionControl(EmotionsType.SURPRISE); // SURPRESA
        //faceOperationResult = faceEmotionControl(EmotionsType.SWEAT); // Suar/cansaço
        //faceOperationResult = faceEmotionControl(EmotionsType.WHISTLE); // APITO

        /*if (faceOperationResult.getErrorCode() == 1) {

            Log.i(TAG, "faceEmotionControl(emotion): A instrução é executada com sucesso");

        } else {
            Log.e(TAG, "Result: " + faceOperationResult.getResult()
                    + "\nDescription: " + faceOperationResult.getDescription()
                    + "\nError code: " + faceOperationResult.getErrorCode()
                    + "\nDescribe contents: " + faceOperationResult.describeContents());
        }*/

    }

    /**
     * {@link ai.api.ui.AIDialog.AIDialogListener} methods starts HERE!!!
     */
    @Override
    public void onResult(AIResponse result) {
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(result));

        Result resultado = result.getResult();

        final String speech = resultado.getFulfillment().getSpeech();
        stopSpeak();
        speak(speech, TextToSpeech.QUEUE_FLUSH);

        handleParameters(resultado.getParameters());
        handleActions(resultado.getAction());
    }

    @Override
    public void onError(AIError error) {
        Log.e(TAG, error.getMessage());
    }

    @Override
    public void onCancelled() {
    }
    /**
     * {@link ai.api.ui.AIDialog.AIDialogListener} methods ends HERE!!!
     */

    /**
     * Taking control of LED eyes to let them show designated emotion.
     *
     * @param emotion - {@link EmotionsType} static constant.
     * @return OperationResult
     */
    private OperationResult faceEmotionControl(EmotionsType emotion) {
        return systemManager.showEmotion(emotion);
    }

    /**
     * To take control of colorful LED lights
     *
     * @param part - {@link LED} static constant.
     * @param mode - {@link LED} static constant.
     * @return OperationResult
     */
    private OperationResult controlOfColorfulLight(byte part, byte mode) {
        LED led = new LED(part, mode);
        return hardWareManager.setLED(led);
    }

    private void turnOnProjector() {

        OperationResult operationResult = projectorManager.switchProjector(true);

        if (operationResult.getErrorCode() == 1) {

            projectorManager.setMode(ProjectorManager.MODE_WALL);
            this.projectorOn = true;

        } else if (operationResult.getErrorCode() < 0) {

            this.projectorOn = false;
        }
    }

    /**
     * Inicia o aplicativo Movie.
     */
    private void starMovie() {
     /*   Intent intent = new Intent(Intent.ACTION_VIEW);
        File file = new File(Environment.getExternalStorageDirectory().getPath() + "Documents");
        intent.setDataAndType(Uri.fromFile(file), "video*//*");
        startActivity(intent);*/

        Intent playVideo = new Intent(getApplicationContext(), ProjetarVideoActivity.class);
        playVideo.putExtra(EXTRA_PROJECTOR_ON, this.projectorOn);
        startActivity(playVideo);
    }

    /**
     * Inicia o aplicativo Music.
     */
    private void startMusic() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        File file = new File(Environment.getExternalStorageDirectory().getPath() + "Documents");
        intent.setDataAndType(Uri.fromFile(file), "audio/*");
        startActivity(intent);
    }

    /**
     * Método que faz o robô falar. Usando o Mecanismo de Convesão de Texto em Voz do Google.
     *
     * @param text      - Texto para ser convertido em voz.
     * @param queueMode - {@link TextToSpeech} static constants.
     */
    private void speak(@NonNull final String text, final int queueMode) {

        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {

                    final Locale ptBR = new Locale("pt", "BR");

                    if (textToSpeech.isLanguageAvailable(ptBR) == TextToSpeech.LANG_COUNTRY_AVAILABLE) {

                        textToSpeech.setLanguage(ptBR);

                        if (!TextUtils.isEmpty(text)) {

                            if (text.length() <= TextToSpeech.getMaxSpeechInputLength()) {

                                controlOfColorfulLight(LED.PART_RIGHT_HAND, LED.MODE_BLUE);// LED do braço direito amarelo.
                                controlOfColorfulLight(LED.PART_LEFT_HAND, LED.MODE_BLUE); // LED do braço esquerdo amarelo.
                                textToSpeech.speak(text, queueMode, null);

                            } else {

                                Toast.makeText(getApplicationContext(),
                                        "Tamanho máx. do texto = "
                                                + TextToSpeech.getMaxSpeechInputLength(),
                                        Toast.LENGTH_SHORT).show();
                            }

                        } else {

                            Toast.makeText(getApplicationContext(),
                                    "Sem texto a ser convertido.", Toast.LENGTH_SHORT).show();
                        }

                    } else {

                        Toast.makeText(getApplicationContext(), "Idioma pt-BR não disponível.",
                                Toast.LENGTH_SHORT).show();
                    }

                } else {

                    Toast.makeText(getApplicationContext(),
                            "Your device don't support Speech output",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * Método que faz o robô parar de falar.
     */
    private void stopSpeak() {
        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
    }

    /**
     * Reconhece as pessoas cadastradas no aplicativo Family
     */
    private void reconhecimentoFacial() {

        headMotionManager.dohorizontalCenterLockMotion();// Posiciona a cabeça centralizada horizontalmente

        final AbsoluteAngleHeadMotion absoluteAngleHeadMotion = new AbsoluteAngleHeadMotion(
                AbsoluteAngleHeadMotion.ACTION_VERTICAL, 30);
        headMotionManager.doAbsoluteAngleMotion(absoluteAngleHeadMotion);// Levanta a cabeça

        mediaManager.setMediaListener(new FaceRecognizeListener() {
            @Override
            public void recognizeResult(List<FaceRecognizeBean> list) {
                for (FaceRecognizeBean bean : list) {

                    Log.i(TAG, new GsonBuilder().setPrettyPrinting().create().toJson(bean));

                    if (!TextUtils.isEmpty(bean.getUser())) {
                        // Usuário reconhecido.
                    }
                }
            }
        });
    }

    public void listenButton(View view) {
        stopSpeak();
        aiDialog.showAndListen();
    }

    /**
     * Método para lidar com as ações.
     *
     * @param action = {@link Result#getAction()}
     */
    private void handleActions(final String action) {

        Log.i(TAG, "Action: " + action);

        if (!StringUtils.isEmpty(action)) {

            switch (action) {

                case "input.welcome":
                    linearLayoutButton.setVisibility(View.VISIBLE);

                    new CountDownTimer(10000, 2000) {
                        @Override
                        public void onTick(long millisUntilFinished) {

                        }

                        @Override
                        public void onFinish() {
                            turnOnProjector();
                        }
                    }.start();

                    break;

                case "escolha_cenario":

                    ArrayList<String> cenarios = getValues();

                    for (String cenario : cenarios) {

                        switch (cenario) {

                            case "\"tour virtual\"":

                                speak("Vamos ali na parede, eu projeto para você.", TextToSpeech.QUEUE_FLUSH);
                                starMovie();
                                break;

                            case "\"área de livros de leitura\"":

                                speak("Siga-me que eu te levo até lá.", TextToSpeech.QUEUE_FLUSH);
                                walk();

                                break;
                        }
                    }

                    break;
            }
        }
    }

    /**
     * Método para lidar com os parâmetros.
     *
     * @param parameters = {@link Result#getParameters()}
     */
    private void handleParameters(HashMap<String, JsonElement> parameters) {

        if (parameters != null && !parameters.isEmpty()) {

            ArrayList<String> values = new ArrayList<>();

            Log.i(TAG, "Parameters: ");

            for (final Map.Entry<String, JsonElement> entry : parameters.entrySet()) {

                Log.i(TAG, String.format("%s: %s", entry.getKey(), entry.getValue().toString()));
                values.add(entry.getValue().toString());
            }
            setValues(values);
        }
    }

    /**
     * Método que responde ao toque em uma View
     */
    public void clickedViews(View view) {

        switch (view.getId()) {

            case R.id.btnTourVirtual:

                speak("Vamos ali na parede, eu projeto para você.", TextToSpeech.QUEUE_FLUSH);
                starMovie();

                break;

            case R.id.btnAreaDeLeitura:

                speak("Siga-me que eu te levo até lá.", TextToSpeech.QUEUE_FLUSH);
                walk();

                break;

            case R.id.receivedMessageTextView:

                receivedMessageTextView.setVisibility(View.INVISIBLE);
                connectServer();

                break;
        }
    }

    /**
     * Faz o robô andar
     */
    private void walk() {

        final RelativeAngleWheelMotion relativeAngleWheelMotion = new RelativeAngleWheelMotion(
                RelativeAngleWheelMotion.TURN_RIGHT, 7, 180);// Configurações = girar a direita; velocidade = 7; a 180 graus.
        wheelMotionManager.doRelativeAngleMotion(relativeAngleWheelMotion);// Executa o comando com a configuração anterior

        // Configurando um delay de 5s (5 * 1000ms) entre os comandos enviados as rodas.
        new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {

                // Quando finalizado o delay, o seguinte comando é executado.
                final DistanceWheelMotion distanceWheelMotion = new DistanceWheelMotion(
                        DistanceWheelMotion.ACTION_FORWARD_RUN, 10, 250);
                wheelMotionManager.doDistanceMotion(distanceWheelMotion);
            }
        }.start();
    }

    /**
     * Antes que o aplicativo se comunique usando o Bluetooth, é necessário verificar se o
     * dispositivo permite Bluetooth e, caso permita, se o Bluetooth está ativado
     */
    private void setUpBluetooth() {

        // 1º Obtenha o BluetoothAdapter

        // Retornará um BluetoothAdapter que representa o próprio adaptador Bluetooth do dispositivo
        // (o rádio Bluetooth).
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Há um adaptador Bluetooth para o sistema completo e o aplicativo pode usar esse objeto
        // para interagir com ele.

        // Se getDefaultAdapter() retornar nulo, o dispositivo não permite Bluetooth e fim de papo.
        if (bluetoothAdapter == null) {

            Toast.makeText(getApplicationContext(), "O dispositivo não permite Bluetooth",
                    Toast.LENGTH_SHORT).show();

            new CountDownTimer(5000, 1000) {

                /**
                 * Callback fired on regular interval.
                 *
                 * @param millisUntilFinished The amount of time until finished.
                 */
                @Override
                public void onTick(long millisUntilFinished) {

                }

                /**
                 * Callback fired when the time is up.
                 */
                @Override
                public void onFinish() {

                    // Ao final do contador
                    finish();// Encerra a activity
                }
            }.start();

        } else {

            // 2º Ativar Bluetooth

            // É necessário assegurar a ativação do Bluetooth. Chame isEnabled() para verificar se o
            // Bluetooth está ativado no momento. Se o método retornar false, o Bluetooth está
            // desativado.
            if (!bluetoothAdapter.isEnabled()) {

                enableBluetooth();
            }
        }
    }

    /**
     * Solicita ao usuário que habilite o Bluetooth
     */
    private void enableBluetooth() {

        // Para solicitar a ativação do Bluetooth, chame startActivityForResult() com o
        // Intent de ação ACTION_REQUEST_ENABLE.
        Intent enableBlIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBlIntent, REQUEST_ENABLE_BT);

        // Será emitida uma solicitação de ativação do Bluetooth por meio das configurações
        // do sistema (sem interromper o aplicativo).

    }

    /**
     * Para conectar dois dispositivos, um deve atuar como servidor, mantendo um
     * {@link BluetoothServerSocket} aberto. O objetivo do soquete do servidor é ouvir solicitações
     * de conexão de entrada e, quando uma for aceita, fornecer um {@link BluetoothSocket} conectado.
     * Quando o {@link BluetoothSocket} é adquirido do {@link BluetoothServerSocket}, o soquete do
     * servidor Bluetooth pode (e deve) ser descartado a menos que você queria aceitar mais conexões.
     */
    private void connectServer() {

        if (bluetoothAdapter.isEnabled()) {

            try {

                this.bluetoothServerSocket = this.bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                        "Bluetooth Server", UUID.fromString(getString(R.string.uuid)));


                // Não se deve executar a chamada de accept() no encadeamento de IU da atividade
                // principal, pois é uma chamada bloqueadora que impede todas as interações com o
                // aplicativo. Normalmente, faz sentido fazer o trabalho completo com um
                // BluetoothServerSocket ou BluetoothSocket em um novo encadeamento, gerenciado pelo
                // aplicativo.
                new Thread(new Runnable() {

                    @Override
                    public void run() {

                        // Keep listening until exception occurs or a bluetoothSocket is returned
                        while (true) {

                            try {

                                // Essa é uma chamada bloqueadora. Ela retornará quando uma conexão for
                                // aceita ou ocorrer uma exceção. A conexão só será aceita quando um
                                // dispositivo remoto enviar uma solicitação de conexão com um UUID
                                // correspondente ao registrado nesse soquete do servidor ouvinte.
                                // Quando bem-sucedido, accept() retornará um BluetoothSocket conectado
                                bluetoothSocket = bluetoothServerSocket.accept();

                            } catch (IOException e) {

                                Log.e(TAG, e.getMessage());
                                break;
                            }

                            // If a connection was accepted
                            if (bluetoothSocket != null) {

                                Log.i(TAG, "A connection is established");

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(getApplicationContext(),
                                                "A connection is established", Toast.LENGTH_SHORT).show();
                                    }
                                });

                                manageConnectedSocket(bluetoothSocket);// Do work to manage the connection (in a separate thread)

                                try {

                                    // A menos que você queira aceitar mais conexões, chame close().
                                    bluetoothServerSocket.close();

                                    // Isso libera o soquete do servidor e todos os seus recursos, MAS não
                                    // fecha o BluetoothSocket conectado retornado por accept().
                                    // Ao contrário do TCP/IP, o RFCOMM somente permite um cliente conectado
                                    // por canal em um determinado momento. Portanto, na maioria dos casos,
                                    // faz sentido chamar close() no BluetoothServerSocket imediatamente
                                    // depois de aceitar um soquete conectado.

                                } catch (IOException e) {

                                    Log.e(TAG, e.getMessage());
                                }
                                break;
                            }
                        }
                    }
                }).start();


            } catch (IOException e) {

                Log.e(TAG, e.getMessage());
            }

        } else {

            enableBluetooth();
        }
    }

    /**
     * Método que inicia o encadeamento para receber dados transferidos pelo Bluetooth Client.
     */
    private void manageConnectedSocket(final BluetoothSocket socket) {

        try {

            final InputStream inputStream = socket.getInputStream();

            new Thread(new Runnable() {
                @Override
                public void run() {

                    byte[] buffer = new byte[1024];
                    int bytes;

                    while (true) {

                        try {

                            bytes = inputStream.read(buffer);

                            final String receivedMessage = new String(buffer, 0, bytes);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    receivedMessageTextView.setText(receivedMessage);
                                }
                            });

                            makeRequest(receivedMessage);

                        } catch (IOException e) {

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(),
                                            "Bluetooth Socket closed", Toast.LENGTH_SHORT)
                                            .show();
                                    receivedMessageTextView.setVisibility(View.VISIBLE);
                                    receivedMessageTextView.setText(getString(
                                            R.string.open_bluetooth_server));
                                }
                            });
                            Log.e(TAG, e.getMessage());

                            break;
                        }

                    }

                }
            }).start();

        } catch (IOException e) {

            Log.e(TAG, e.getMessage());
        }
    }

    /**
     * Faz o {@link AIDataService#request(AIRequest)} com a mensagem recebida via Bluetooth
     *
     * @param query {@link String} texto que será processado - pelo Dialogflow Agent -  como
     *              linguagem natural
     */
    private void makeRequest(final String query) {

        DialogFlowTask dialogFlowTask = new DialogFlowTask();

        AIRequest params = new AIRequest();
        params.setQuery(query);

        dialogFlowTask.execute(params);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {

            case REQUEST_ENABLE_BT:

                if (resultCode == RESULT_CANCELED) {

                    Toast.makeText(getApplicationContext(), "O Bluetooth é necessário",
                            Toast.LENGTH_SHORT).show();

                } else if (resultCode == RESULT_OK) {

                    Toast.makeText(getApplicationContext(), "Bluetooth ativado",
                            Toast.LENGTH_SHORT).show();
                }

                break;
        }
    }

    private class DialogFlowTask extends AsyncTask<AIRequest, Void, AIResponse> {

        /**
         * Override this method to perform a computation on a background thread. The
         * specified parameters are the parameters passed to {@link #execute}
         * by the caller of this task.
         * <p>
         * This method can call {@link #publishProgress} to publish updates
         * on the UI thread.
         *
         * @param aiRequests The parameters of the task.
         * @return A result, defined by the subclass of this task.
         * @see #onPreExecute()
         * @see #onPostExecute
         * @see #publishProgress
         */
        @Override
        protected AIResponse doInBackground(AIRequest... aiRequests) {

            final AIRequest request = aiRequests[0];

            try {

                return aiDataService.request(request);

            } catch (AIServiceException e) {

                onError(new AIError(e.getMessage()));
            }

            return null;
        }

        @Override
        protected void onPostExecute(AIResponse response) {
            super.onPostExecute(response);

            if (response != null) {
                onResult(response);
            }
        }
    }
}
