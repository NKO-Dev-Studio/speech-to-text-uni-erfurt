package studio.nkodev.stt.engine.whisper;

import com.google.gson.annotations.SerializedName;

/**
 * Mapper class for resulting program argument json of whisper-cli-mock.sh
 *
 * @author Nico Kotlenga (nico@nko-dev.studio)
 * @since 28.02.26
 */
public record WhisperProgramArgument(
    boolean verbose,
    String language,
    String device,
    String model,
    @SerializedName("output_format") String output_format,
    @SerializedName("output_dir") String outputDir,
    String audioFilePath) {}
