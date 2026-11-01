/*
 * Copyright 2012 Kulikov Dmitriy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.lcdui;

import java.util.HashMap;

import javax.microedition.lcdui.event.CanvasEvent;
import javax.microedition.lcdui.event.SimpleEvent;
import javax.microedition.util.ContextHolder;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.util.Log;

public abstract class Canvas extends Displayable {
	public static final int KEY_POUND = 35;
	public static final int KEY_STAR = 42;
	public static final int KEY_NUM0 = 48;
	public static final int KEY_NUM1 = 49;
	public static final int KEY_NUM2 = 50;
	public static final int KEY_NUM3 = 51;
	public static final int KEY_NUM4 = 52;
	public static final int KEY_NUM5 = 53;
	public static final int KEY_NUM6 = 54;
	public static final int KEY_NUM7 = 55;
	public static final int KEY_NUM8 = 56;
	public static final int KEY_NUM9 = 57;
	
	public static final int KEY_UP = -1;
	public static final int KEY_DOWN = -2;
	public static final int KEY_LEFT = -3;
	public static final int KEY_RIGHT = -4;
	public static final int KEY_FIRE = -5;
	public static final int KEY_SOFT_LEFT = -6;
	public static final int KEY_SOFT_RIGHT = -7;
	public static final int KEY_CLEAR = -8;
	public static final int KEY_SEND = -10;
	public static final int KEY_END = -11;

	public static final int UP = 1;
	public static final int LEFT = 2;
	public static final int RIGHT = 5;
	public static final int DOWN = 6;
	public static final int FIRE = 8;
	public static final int GAME_A = 9;
	public static final int GAME_B = 10;
	public static final int GAME_C = 11;
	public static final int GAME_D = 12;

	private static HashMap<Integer, Integer> androidToMIDP;
	private static HashMap<Integer, Integer> midpToAndroid;
	private static HashMap<Integer, Integer> keyCodeToGameAction;
	private static HashMap<Integer, Integer> gameActionToKeyCode;
	private static HashMap<Integer, String> keyCodeToKeyName;

	static
	{
		androidToMIDP = new HashMap();
		midpToAndroid = new HashMap();
		keyCodeToGameAction = new HashMap();
		gameActionToKeyCode = new HashMap();
		keyCodeToKeyName = new HashMap();

		mapKeyCode(KeyEvent.KEYCODE_0, 48, 0, "0");
		mapKeyCode(KeyEvent.KEYCODE_1, 49, 0, "1");
		mapKeyCode(KeyEvent.KEYCODE_2, 50, 1, "2");
		mapKeyCode(KeyEvent.KEYCODE_3, 51, 0, "3");
		mapKeyCode(KeyEvent.KEYCODE_4, 52, 2, "4");
		mapKeyCode(KeyEvent.KEYCODE_5, 53, 8, "5");
		mapKeyCode(KeyEvent.KEYCODE_6, 54, 5, "6");
		mapKeyCode(KeyEvent.KEYCODE_7, 55, 9, "7");
		mapKeyCode(KeyEvent.KEYCODE_8, 56, 6, "8");
		mapKeyCode(KeyEvent.KEYCODE_9, 57, 10, "9");
		mapKeyCode(KeyEvent.KEYCODE_STAR, 42, 11, "ASTERISK");
		mapKeyCode(KeyEvent.KEYCODE_POUND, 35, 12, "POUND");
		mapKeyCode(KeyEvent.KEYCODE_DPAD_UP, -1, 1, "UP");
		mapKeyCode(KeyEvent.KEYCODE_DPAD_DOWN, -2, 6, "DOWN");
		mapKeyCode(KeyEvent.KEYCODE_DPAD_LEFT, -3, 2, "LEFT");
		mapKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT, -4, 5, "RIGHT");
		mapKeyCode(KeyEvent.KEYCODE_DPAD_CENTER, -5, 8, "SELECT");
		mapKeyCode(KeyEvent.KEYCODE_SOFT_LEFT, -6, 0, "SOFT1");
		mapKeyCode(KeyEvent.KEYCODE_SOFT_RIGHT, -7, 0, "SOFT2");
		mapKeyCode(KeyEvent.KEYCODE_CLEAR, -8, 0, "CLEAR");
		mapKeyCode(KeyEvent.KEYCODE_CALL, -10, 0, "SEND");
		mapKeyCode(KeyEvent.KEYCODE_ENDCALL, -11, 0, "END");

		mapGameAction(1, -1, "UP");
		mapGameAction(2, -3, "LEFT");
		mapGameAction(5, -4, "RIGHT");
		mapGameAction(6, -2, "DOWN");
		mapGameAction(8, -5, "SELECT");
		mapGameAction(9, 55, "7");
		mapGameAction(10, 57, "9");
		mapGameAction(11, 42, "ASTERISK");
		mapGameAction(12, 35, "POUND");
	}

	private static void mapKeyCode(int androidKeyCode, int midpKeyCode, int gameAction, String keyName) {
		androidToMIDP.put(androidKeyCode, midpKeyCode);
		midpToAndroid.put(midpKeyCode, androidKeyCode);

		keyCodeToGameAction.put(midpKeyCode, gameAction);
		keyCodeToKeyName.put(midpKeyCode, keyName);
	}

	private static void mapGameAction(int gameAction, int keyCode, String keyName) {
		gameActionToKeyCode.put(gameAction, keyCode);
		keyCodeToKeyName.put(keyCode, keyName);
	}

	/**
	 * Re-binds one game action to a different Android key code for the game that is
	 * about to start. The MIDP key code expected by the MIDlet is preserved.
	 */
	public static synchronized void remapGameAction(int gameAction, int androidKeyCode, String keyName) {
		Integer stored = gameActionToKeyCode.get(gameAction);
		int midpKeyCode = stored != null ? stored : defaultMidpKeyCode(gameAction);

		java.util.Iterator<java.util.Map.Entry<Integer, Integer>> iterator =
				androidToMIDP.entrySet().iterator();
		while (iterator.hasNext()) {
			if (iterator.next().getValue() == midpKeyCode) {
				iterator.remove();
			}
		}

		androidToMIDP.put(androidKeyCode, midpKeyCode);
		keyCodeToGameAction.put(midpKeyCode, gameAction);
		gameActionToKeyCode.put(gameAction, midpKeyCode);
		keyCodeToKeyName.put(midpKeyCode, keyName);
	}

	public static synchronized void remapSoftKey(int androidKeyCode, boolean left) {
		int midpKeyCode = left ? -6 : -7;
		androidToMIDP.put(androidKeyCode, midpKeyCode);
		keyCodeToGameAction.put(midpKeyCode, 0);
		keyCodeToKeyName.put(midpKeyCode, left ? "SOFT1" : "SOFT2");
	}

	private static int defaultMidpKeyCode(int gameAction) {
		switch (gameAction) {
			case UP: return -1;
			case DOWN: return -2;
			case LEFT: return -3;
			case RIGHT: return -4;
			case FIRE: return -5;
			case GAME_A: return 55;
			case GAME_B: return 57;
			case GAME_C: return 42;
			case GAME_D: return 35;
			default: return -(gameAction << 8);
		}
	}

	public static int convertAndroidKeyCode(int keyCode) {
		Integer res = androidToMIDP.get(keyCode);

		if (res != null) {
			return res;
		} else {
			return -(keyCode << 8);
		}
	}

	public int getKeyCode(int gameAction) {
		return getKeyCodeOrig(gameAction);
	}
	public static int getKeyCodeOrig(int gameAction) {
		Integer res = gameActionToKeyCode.get(gameAction);

		if (res != null) {
			return res;
		} else {
			throw new IllegalArgumentException("unknown game action " + gameAction);
		}
	}

	public int getGameAction(int keyCode) {
		return getGameActionOrig(keyCode);
	}
	public static int getGameActionOrig(int keyCode) {
		Integer res = keyCodeToGameAction.get(keyCode);

		if (res != null) {
			return res;
		} else {
			return 0;
		}
	}

	public String getKeyName(int keyCode) {
		return getKeyNameOrig(keyCode);
	}
	public static String getKeyNameOrig(int keyCode) {
		return keyCodeToKeyName.get(keyCode);
	}

	private class InnerView extends SurfaceView {
		public InnerView(Context context) {
			super(context);

			if (graphicsMode == 0) {
				setLayerType(View.LAYER_TYPE_SOFTWARE, null);
			}
		}

		public boolean onKeyDown(int keyCode, KeyEvent event) {
			keyCode = convertAndroidKeyCode(keyCode);

			if (event.getRepeatCount() == 0) {
				if (overlay == null || !overlay.keyPressed(keyCode)) {
					postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.KEY_PRESSED, keyCode));
				}
			} else {
				if (overlay == null || !overlay.keyRepeated(keyCode)) {
					postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.KEY_REPEATED, keyCode));
				}
			}

			return super.onKeyDown(keyCode, event);
		}

		public boolean onKeyUp(int keyCode, KeyEvent event) {
			keyCode = convertAndroidKeyCode(keyCode);

			if (overlay == null || !overlay.keyReleased(keyCode)) {
				postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.KEY_RELEASED, keyCode));
			}

			return super.onKeyUp(keyCode, event);
		}

		public boolean onTouchEvent(MotionEvent event) {
			if (!touchInput) {
				return super.onTouchEvent(event);
			}

			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					if (overlay != null) {
						overlay.show();
					}

				case MotionEvent.ACTION_POINTER_DOWN:
					int index = event.getActionIndex();

					if (overlay == null || !overlay.pointerPressed(index, event.getX(index), event.getY(index))) {
						postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.POINTER_PRESSED, index, convertPointerX(event.getX()), convertPointerY(event.getY())));
					}

					break;

				case MotionEvent.ACTION_MOVE:
					int pointerCount = event.getPointerCount();
					int historySize = event.getHistorySize();

					//System.out.println("Pointer moved, historySize = " + historySize);

					for (int h = 0; h < historySize; h++) {
						for (int p = 0; p < pointerCount; p++) {
							if (overlay == null || !overlay.pointerDragged(p, event.getHistoricalX(p, h), event.getHistoricalY(p, h))) {
								postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.POINTER_DRAGGED, p, convertPointerX(event.getHistoricalX(p, h)), convertPointerY(event.getHistoricalY(p, h))));
							}
						}
					}

					for (int p = 0; p < pointerCount; p++) {
						if (overlay == null || !overlay.pointerDragged(p, event.getX(p), event.getY(p))) {
							postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.POINTER_DRAGGED, p, convertPointerX(event.getX(p)), convertPointerY(event.getY(p))));
						}
					}

					//System.out.println("Pointer event processed");

					break;

				case MotionEvent.ACTION_UP:
					if (overlay != null) {
						overlay.hide();
					}

				case MotionEvent.ACTION_POINTER_UP:
					index = event.getActionIndex();

					if (overlay == null || !overlay.pointerReleased(index, event.getX(index), event.getY(index))) {
						postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.POINTER_RELEASED, index, convertPointerX(event.getX()), convertPointerY(event.getY())));
					}

					break;

				default:
					return super.onTouchEvent(event);
			}

			return true;
		}
	}

	private class SurfaceCallback implements SurfaceHolder.Callback {
		public void surfaceChanged(SurfaceHolder holder, int format, int newwidth, int newheight) {
			try {
				synchronized (paintsync) {
					displayWidth = newwidth;
					displayHeight = newheight;

					updateSize(true);
				}

				postEvent(paintEvent);
			} catch (Throwable t) {
				recordPaintError(t);
			}
		}

		public void surfaceCreated(SurfaceHolder holder) {
			synchronized (paintsync) {
				surfacevalid = true;
				surfaceReady = true;
				postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.SHOW_NOTIFY));
			}
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			synchronized (paintsync) {
				surfacevalid = false;
				surfaceReady = false;
				postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.HIDE_NOTIFY));
			}
		}
	}

	private void recordPaintError(Throwable t) {
		lastPaintError = t.toString();
		paintErrorCount++;
		t.printStackTrace();
	}

	private void drawOverlay() {
		if (overlay == null) {
			return;
		}

		try {
			overlay.paint(graphics);
		} catch (Throwable t) {
			recordPaintError(t);
		}
	}

	private void drawFps(android.graphics.Canvas canvas) {
		if (!showFps || canvas == null) {
			return;
		}

		long now = System.currentTimeMillis();
		if (fpsStartTime == 0) {
			fpsStartTime = now;
		}
		fpsFrames++;
		if (now - fpsStartTime >= 1000) {
			currentFps = fpsFrames;
			fpsFrames = 0;
			fpsStartTime = now;
		}

		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setTextSize(28);
		paint.setColor(Color.YELLOW);
		paint.setStyle(Paint.Style.FILL);
		canvas.drawText(currentFps + " FPS", 12, 34, paint);
	}

	private class PaintEvent extends Event implements EventFilter {
		public void process() {
			synchronized (paintsync) {
				if (!surfacevalid || holder == null) {
					return;
				}

				if (fpsLimit > 0) {
					long now = System.currentTimeMillis();
					long frameInterval = 1000L / fpsLimit;
					if (now - lastFrameTime < frameInterval) {
						continuepaint = true;
						return;
					}
					lastFrameTime = now;
				}

				if (useOffscreen) {
					if (offscreen == null) {
						return;
					}

					graphics.setCanvas(offscreen.getCanvas());

					boolean paintFailed = false;

					try {
						paint(graphics);
					} catch (Throwable t) {
						paintFailed = true;
						recordPaintError(t);

						graphics.resetTranslation();
						graphics.resetClip();
					}

					android.graphics.Canvas screen = null;

					try {
						screen = holder.lockCanvas();
						graphics.setCanvas(screen);

						if (graphics.hasCanvas()) {
							graphics.clear(backgroundColor);
							graphics.drawImage(offscreen, onX, onY, onWidth, onHeight, filter, 255);

							drawOverlay();
							drawFps(screen);

							if (!paintFailed) {
								frameCount++;
							}
						}
					} catch (Throwable t) {
						recordPaintError(t);
					} finally {
						if (screen != null) {
							try {
								holder.unlockCanvasAndPost(screen);
							} catch (Throwable t) {
								recordPaintError(t);
							}
						}
					}
				} else {
					android.graphics.Canvas screen = null;

					try {
						screen = holder.lockCanvas();
						graphics.setCanvas(screen);

						if (graphics.hasCanvas()) {
							graphics.clear(backgroundColor);
							graphics.setWindow(onX, onY, onWidth, onHeight);

							boolean paintFailed = false;

							try {
								paint(graphics);
							} catch (Throwable t) {
								paintFailed = true;
								recordPaintError(t);

								graphics.resetTranslation();
								graphics.resetClip();
							}

							graphics.resetWindow();

							drawOverlay();
							drawFps(screen);

							if (!paintFailed) {
								frameCount++;
							}
						}
					} catch (Throwable t) {
						recordPaintError(t);
					} finally {
						if (screen != null) {
							try {
								holder.unlockCanvasAndPost(screen);
							} catch (Throwable t) {
								recordPaintError(t);
							}
						}
					}
				}

				continuepaint = true;
			}
		}

		public void recycle() {
		}

		public int enqueued = 0;

		public void enterQueue() {
			//System.out.println("enterQueue from " + enqueued);
			enqueued++;
			//System.out.println("enterQueue now " + enqueued);
		}

		public void leaveQueue() {
			//System.out.println("leaveQueue from " + enqueued);
			enqueued--;
			//System.out.println("leaveQueue now " + enqueued);
		}

		/**
		 * В очереди должно быть не более двух перерисовок.
		 * 
		 * Одна не обеспечит плавности, а если делать больше двух,
		 * то как определить, насколько именно больше двух их нужно сделать?
		 */
		public boolean placeableAfter(Event event) {
			//System.out.println("placeableAfter from " + enqueued);
			return enqueued < 2;

//			if(event == this)
//			{
//				if(continuepaint)			// если предыдущая перерисовка закончилась
//				{
//					continuepaint = false;
//					return true;			// можно ставить в очередь новую
//				}
//				else
//				{
//					return false;
//				}
//			}
//			
//			return true;
		}

		public boolean accept(Event event) {
			return event == this;
		}

//		public String toString()
//		{
//			return "repaint()";
//		}
	}

	private SimpleEvent initViewEvent = new SimpleEvent()
	{
		public void process() {
			view = new InnerView(getParentActivity());

			holder = view.getHolder();
			holder.addCallback(new SurfaceCallback());

			view.setFocusableInTouchMode(true);
			view.requestFocus();

			synchronized (waiter) {
				waiter.notifyAll();
			}
		}
	};

	private final Object waiter = new Object();
	private final Object paintsync = new Object();

	private PaintEvent paintEvent = new PaintEvent();

	private boolean surfacevalid = false;
	private boolean continuepaint = true;

	private InnerView view;
	private SurfaceHolder holder;
	private Graphics graphics = new Graphics();

	private int width, height;

	private int displayWidth;
	private int displayHeight;

	private static int virtualWidth;
	private static int virtualHeight;

	private static boolean scaleToFit;
	private static boolean keepAspectRatio;
	private static boolean filter;
	private static int backgroundColor;

	private static int screenScaleType = 1;
	private static int screenGravity = 2;
	private static int screenScaleRatio = 100;
	private static boolean immediateMode = false;
	private static boolean showFps = false;
	private static int fpsLimit = 0;
	private static boolean touchInput = true;
	private static int graphicsMode = 1;

	private static long lastFrameTime = 0;
	private static long fpsStartTime = 0;
	private static int fpsFrames = 0;
	private static int currentFps = 0;

	private static volatile long frameCount = 0;
	private static volatile int paintErrorCount = 0;
	private static volatile boolean surfaceReady = false;
	private static volatile String lastPaintError = null;

	/** Number of frames actually pushed to the screen; 0 means the game never rendered. */
	public static long getFrameCount() {
		return frameCount;
	}

	/** Number of paint attempts that ended in an exception. */
	public static int getPaintErrorCount() {
		return paintErrorCount;
	}

	public static boolean isSurfaceReady() {
		return surfaceReady;
	}

	public static String getLastPaintError() {
		return lastPaintError;
	}

	private Image offscreen;
	private int onX, onY, onWidth, onHeight;
	private boolean useOffscreen;

	private Overlay overlay;

	public Canvas() {
		displayWidth = Display.getWidth();
		displayHeight = Display.getHeight();
		Log.d("Canvas", "Constructor. w=" + displayWidth + " h=" + displayHeight);

		updateSize(false);
	}

	public static void setVirtualSize(int virtualWidth, int virtualHeight, boolean scaleToFit, boolean keepAspectRatio) {
		Canvas.virtualWidth = virtualWidth;
		Canvas.virtualHeight = virtualHeight;

		Canvas.scaleToFit = scaleToFit;
		Canvas.keepAspectRatio = keepAspectRatio;
		Canvas.screenScaleType = !scaleToFit ? 0 : (keepAspectRatio ? 1 : 2);
	}

	/**
	 * Applies the per-game run configuration. Called before the MIDlet is loaded.
	 *
	 * @param scaleType 0 = no scaling, 1 = fit inside the screen, 2 = stretch to screen
	 * @param gravity 0 = left, 1 = top, 2 = center, 3 = right, 4 = bottom
	 * @param scaleRatio percentage applied to the rendered size (100 = untouched)
	 * @param shaderValue 0 = use the filter flag, 1 = force smooth filtering, 2 = force nearest
	 */
	public static void applyProfile(int width, int height, int scaleType, int gravity,
			int scaleRatio, boolean filterBitmap, int background,
			boolean immediate, boolean showFpsValue, int fpsLimitValue,
			boolean touch, int graphicsModeValue, int shaderValue) {
		virtualWidth = Math.max(1, width);
		virtualHeight = Math.max(1, height);
		screenScaleType = scaleType;
		screenGravity = gravity;
		screenScaleRatio = Math.max(10, Math.min(200, scaleRatio));
		filter = shaderValue == 1 || (shaderValue == 0 && filterBitmap);
		backgroundColor = background | 0xFF000000;
		immediateMode = immediate;
		showFps = showFpsValue;
		fpsLimit = Math.max(0, Math.min(240, fpsLimitValue));
		touchInput = touch;
		graphicsMode = graphicsModeValue;
		lastFrameTime = 0;
		fpsStartTime = 0;
		fpsFrames = 0;
		currentFps = 0;
		frameCount = 0;
		paintErrorCount = 0;
		surfaceReady = false;
		lastPaintError = null;
	}

	public static void setBackgroundColor(int color) {
		backgroundColor = color | 0xFF000000;
	}

	public static void setFilterBitmap(boolean filter) {
		Canvas.filter = filter;
	}

	public void setOverlay(Overlay ov) {
		if (overlay != null) {
			overlay.setTarget(null);
		}

		if (ov != null) {
			ov.setTarget(this);
		}

		overlay = ov;
	}

	/**
	 * Обновить размер и положение виртуального экрана относительно реального.
	 * @param post следует ли оповещать об этих изменениях сам холст и картинку двойной буферизации
	 */
	private void updateSize(boolean post) {
		/*
		 * Превращаем размеры виртуального экрана
		 * в размеры видимого для мидлета холста.
		 * 
		 * При этом учитываем, что один или оба виртуальных размера могут быть
		 * меньше нуля, что означает автоподбор этого размера так,
		 * чтобы получившийся холст имел то же соотношение сторон,
		 * что и реальный экран устройства.
		 */

		if (virtualWidth < 0) {
			if (virtualHeight < 0) {
				/*
				 * не задано ничего - холст размером в экран
				 */

				width = displayWidth;
				height = displayHeight;
			} else {
				/*
				 * задана только высота холста
				 * ширина подбирается по соотношению сторон реального экрана
				 */

				width = displayWidth * virtualHeight / displayHeight;
				height = virtualHeight;
			}
		} else if (virtualHeight < 0) {
			/*
			 * задана только ширина холста
			 * высота подбирается по соотношению сторон реального экрана
			 */

			width = virtualWidth;
			height = displayHeight * virtualWidth / displayWidth;
		} else {
			/*
			 * ширина и высота холста жестко заданы
			 */

			width = virtualWidth;
			height = virtualHeight;
		}

		/*
		 * Превращаем размеры холста в размер картинки,
		 * которая будет отображаться на экране устройсва.
		 */

		switch (screenScaleType) {
			case 0:
				// tanpa penskalaan
				onWidth = width;
				onHeight = height;
				break;
			case 2:
				// rentangkan penuh tanpa menjaga rasio aspek
				onWidth = displayWidth;
				onHeight = displayHeight;
				break;
			case 1:
			default:
				// sesuaikan ke layar dengan rasio aspek tetap
				onWidth = displayWidth;
				onHeight = height * displayWidth / width;

				if (onHeight > displayHeight) {
					onHeight = displayHeight;
					onWidth = width * displayHeight / height;
				}
				break;
		}

		if (screenScaleType != 0) {
			int ratio = Math.max(10, Math.min(200, screenScaleRatio));
			onWidth = onWidth * ratio / 100;
			onHeight = onHeight * ratio / 100;
		}

		switch (screenGravity) {
			case 0: // kiri
				onX = 0;
				onY = (displayHeight - onHeight) / 2;
				break;
			case 1: // atas
				onX = (displayWidth - onWidth) / 2;
				onY = 0;
				break;
			case 3: // kanan
				onX = displayWidth - onWidth;
				onY = (displayHeight - onHeight) / 2;
				break;
			case 4: // bawah
				onX = (displayWidth - onWidth) / 2;
				onY = displayHeight - onHeight;
				break;
			case 2: // tengah
			default:
				onX = (displayWidth - onWidth) / 2;
				onY = (displayHeight - onHeight) / 2;
				break;
		}

		RectF screen = new RectF(0, 0, displayWidth, displayHeight);
		RectF virtualScreen = new RectF(onX, onY, onX + onWidth, onY + onHeight);

		useOffscreen = !immediateMode;

		if (post) {
			/*
			 * проверяем, нужно ли создавать новую картинку для двойной буферизации,
			 * или сгодится старая
			 */

			if (useOffscreen && (offscreen == null || offscreen.getWidth() != width || offscreen.getHeight() != height)) {
				try {
					offscreen = Image.createImage(width, height);
				} catch (Throwable t) {
					// A virtual screen too large for the heap must not leave a locked,
					// blank surface: fall back to drawing straight to the screen.
					recordPaintError(t);
					offscreen = null;
					useOffscreen = false;
				}
			}

			postEvent(CanvasEvent.getInstance(this, CanvasEvent.SIZE_CHANGED, width, height));
		}

		if (overlay != null) {
			overlay.resize(screen, virtualScreen);
		}

//		System.out.println();
//		System.out.println("Canvas size updated:");
//		System.out.println("Virtual = " + virtualWidth + " x " + virtualHeight);
//		System.out.println("Display = " + displayWidth + " x " + displayHeight);
//		System.out.println("Canvas = " + width + " x " + height);
//		System.out.println("Offscreen = " + onWidth + " x " + onHeight);
//		System.out.println();
	}

	/**
	 * Привести экранные координаты указателя к виртуальным.
	 * 
	 * @param x координата указателя на реальном экране
	 * @return соответствующая координата указателя на виртуальном экране
	 */
	public float convertPointerX(float x) {
		return (x - onX) * virtualWidth / onWidth;
	}

	/**
	 * Привести экранные координаты указателя к виртуальным.
	 * 
	 * @param y координата указателя на реальном экране
	 * @return соответствующая координата указателя на виртуальном экране
	 */
	public float convertPointerY(float y) {
		return (y - onY) * virtualHeight / onHeight;
	}

	public View getDisplayableView() {
		if (view == null) {
			view = new InnerView(getParentActivity());

			holder = view.getHolder();
			holder.addCallback(new SurfaceCallback());

			view.setFocusableInTouchMode(true);
			view.requestFocus();
		}

		return view;
	}

	public void clearDisplayableView() {
		synchronized (paintsync) {
			view = null;
			holder = null;
		}
	}

	public void setFullScreenMode(boolean flag) {
	}

	public boolean hasPointerEvents() {
		return overlay == null;
	}

	public boolean hasPointerMotionEvents() {
		return overlay == null;
	}

	public static boolean hasRepeatEvents() {
		return true;
	}

	public static boolean isDoubleBuffered() {
		return true;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public abstract void paint(Graphics g);

	public void repaint() {
		getEventQueue().postEvent(paintEvent);
	}

	public void repaint(int x, int y, int width, int height) {
		getEventQueue().postEvent(paintEvent);
	}

	/**
	 * После вызова этого метода гарантированно произойдет немедленная перерисовка,
	 * причем вызывающий поток блокируется до ее завершения.
	 */
	public void serviceRepaints() {
		EventQueue queue = getEventQueue();

		/*
		 * порядок блокировки:
		 * 
		 * 1 - queue.this
		 * 2 - queue.queue
		 * 
		 * соответственно, внутри EventQueue порядок должен быть такой же,
		 * иначе возможна взаимная блокировка двух потоков (все повиснет)
		 */

		synchronized (queue) {
			/*
			 * Такая синхронизация фактически приостанавливает обработку событий
			 * непосредственно перед изменением значения currentEvent()
			 * 
			 * Тогда остается всего два варианта:
			 */

			if (queue.currentEvent() == paintEvent) {
				/*
				 * если там сейчас обрабатывается repaint(),
				 * то здесь нужно просто подождать его завершения
				 */

				try {
					queue.wait();
				} catch (InterruptedException ie) {
				}
			} else if (queue.removeEvents(paintEvent)) {
				/*
				 * если же сейчас там обрабатывается что-то другое (не repaint),
				 * но repaint в очереди все же был (и был оттуда удален),
				 * то его нужно синхронно вызвать отсюда
				 */

				paintEvent.run();
			}
		}
	}

	public void showNotify() {
	}

	public void hideNotify() {
	}

	public void sizeChanged(int w, int h) {
	}

	public void keyPressed(int keyCode) {
	}

	public void keyRepeated(int keyCode) {
	}

	public void keyReleased(int keyCode) {
	}

	public void pointerPressed(int pointer, float x, float y) {
		if (pointer == 0) {
			pointerPressed(Math.round(x), Math.round(y));
		}
	}

	public void pointerDragged(int pointer, float x, float y) {
		if (pointer == 0) {
			pointerDragged(Math.round(x), Math.round(y));
		}
	}

	public void pointerReleased(int pointer, float x, float y) {
		if (pointer == 0) {
			pointerReleased(Math.round(x), Math.round(y));
		}
	}

	public void pointerPressed(int x, int y) {
	}

	public void pointerDragged(int x, int y) {
	}

	public void pointerReleased(int x, int y) {
	}
}
