package clearvolume.renderer.jogl.overlay.o3d;

import cleargl.ClearTextRenderer;
import cleargl.GLMatrix;
import clearvolume.renderer.DisplayRequestInterface;
import clearvolume.renderer.jogl.overlay.Overlay2D;
import clearvolume.renderer.processors.Processor;
import clearvolume.renderer.processors.ProcessorResultListener;
import org.apache.commons.math3.stat.descriptive.SynchronizedDescriptiveStatistics;

import javax.media.opengl.GL4;
import java.awt.*;
import java.io.IOException;
import java.nio.FloatBuffer;

/**
 * Drift Path Overlay.
 *
 * @author Ulrik Guenther (2015)
 *
 */

public class DriftOverlay extends PathOverlay	implements
																							ProcessorResultListener<float[]>,
																							Overlay2D
{
  protected FloatBuffer mStartColor = FloatBuffer.wrap(new float[]{0.0f, 1.0f, 0.0f, 1.0f});
  protected FloatBuffer mEndColor = FloatBuffer.wrap(new float[]{1.0f, 0.0f, 0.0f, 1.0f});
  protected SynchronizedDescriptiveStatistics stats;

  protected ClearTextRenderer textRenderer;

  /* (non-Javadoc)
	 * @see clearvolume.renderer.jogl.overlay.Overlay#getName()
	 */
  @Override
  public void init(	GL4 pGL4,
                     DisplayRequestInterface pDisplayRequestInterface) {
    super.init(pGL4, pDisplayRequestInterface);

    super.mStartColor = mStartColor;
    super.mEndColor = mEndColor;
    textRenderer = new ClearTextRenderer(pGL4, false);
  }

  @Override
  public String getName() {
    return "drift-path";
  }

  public void addNewCenterOfMass(float x, float y, float z) {
    mPathPoints.add(x, y, z);
  }

	@Override
	public void notifyResult(Processor<float[]> pSource,
														float[] pResult)
	{
		addNewCenterOfMass(pResult[0], pResult[1], pResult[2]);
	}

  @Override
  public boolean hasChanged2D() {
    return false;
  }

  @Override
  public void render2D(GL4 pGL4, GLMatrix pProjectionMatrix, GLMatrix pInvVolumeMatrix) {
    Font font = null;
    stats = new SynchronizedDescriptiveStatistics();

    String fontPath = "fonts/SourceCodeProLight.ttf";

    try {
      font = Font.createFont(Font.TRUETYPE_FONT, getClass().getResourceAsStream(fontPath)).deriveFont(12.0f);
    } catch (final FontFormatException | IOException e) {
      // use a fallback font in case the original couldn't be found or there has been a problem
      // with the font format
      System.err.println("Could not use \"" + fontPath + "\" (" + e.toString() + "), falling back to Sans.");
      font = new Font("Sans", Font.PLAIN, 12);
    }

    int i = 0;
    for(; i < getPathPoints().capacity(); i = i + 3) {
      final float x = getPathPoints().get(i);
      final float y = getPathPoints().get(i+1);
      final float z = getPathPoints().get(i+2);
      final float dist = (float)Math.sqrt(x*x + y*y + z*z);

      stats.addValue(dist);
    }

    textRenderer.drawTextAtPosition("drift stats: n=" + i/3 + " avg=" + String.format("%.3f", stats.getMean()) + " stddev=" + String.format("%.3f", stats.getStandardDeviation()) + " min=" + String.format("%.3f", stats.getMin()) + " max=" + String.format("%.3f", stats.getMax()), 10, 15, font, FloatBuffer.wrap(new float[]{1.0f, 1.0f, 1.0f}), false);
  }

}