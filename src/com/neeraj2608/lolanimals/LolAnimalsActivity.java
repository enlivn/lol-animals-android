package com.neeraj2608.lolanimals;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.Source;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ViewFlipper;

public class LolAnimalsActivity extends Activity{
	// Thread handling variables
	private static final int PROGRESS_DIALOG_ID = 0;
	private static final int BUSY_DRAWING_ID = 1;
	private final int BOOKMARKACTIVITY_IDENTIFIER = 0;
	private ProgressThread popimgQueueThread;
	private BusyDrawingThread busyDrawingThread;
	private ProgressDialog popimgQueueThreadProgressDialog;
	private ProgressDialog busyDrawingThreadProgressDialog;
	private static final String TAG = "LolCats"; //Debugging only

	// Image display variables
	private Bitmap imgBitMap; //for displaying and saving the image
	private ImageView imgView; //displays the pictures
	private ImageButton nextImgBtn, saveImgBtn, bookmarkImgBtn, prevImgBtn; //next image button
	private InputStream imgStream;
	private BufferedInputStream bufferedimgStream;
	private Queue<String> imgQueue = new LinkedList<String>();
	private List<String>  imgURLListforDB; //must use a list since I do not know how many URLs the user would
																 //possibly want to store
	private ViewFlipper startupImageFlipper;
	private static int pageNum;
	private GestureDetector flingDetector;
	private ImageSwitcher imageSwitcher;
	private Animation slideLeftIn;
	private Animation slideRightIn;
	private Animation slideLeftOut;
	private Animation slideRightOut;
	private final static int SWIPE_MAX_OFF_PATH = 250;
	private final static int SWIPE_MIN_DISTANCE = 120;
	private final static int SWIPE_THRESHOLD_VELOCITY = 200;
	private SimpleDateFormat uniqueFileName = new SimpleDateFormat("ddMMyyyyhhmm");
	File lolCatsCacheDir = null;
	File lolCatsSaveDir = null;
	private static int currDispImage;
	private static int imageCeiling;
	private final int CACHE_MODE = 0;
	private final int SAVE_MODE = 1;
	private DBAdapter db;
	private URL imgURL;
		
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		pageNum = 1;
		
		// --------------------------
		// Check network connectivity
		// --------------------------
		checkNetworkAvailable(LolAnimalsActivity.this);

		// ---------------------------------
		// Configure Fetch Image Button
		// ---------------------------------
		nextImgBtn = (ImageButton) findViewById(R.id.nextImgBtn);
		nextImgBtn.setOnClickListener(handleNextImgBtn);
		
		// ---------------------------------
		// Configure Save Image Button
		// ---------------------------------
		saveImgBtn = (ImageButton) findViewById(R.id.saveImgBtn);
		saveImgBtn.setOnClickListener(handleSaveImgBtn);
		
		// ---------------------------------
		// Configure Bookmark Image Button
		// ---------------------------------
		bookmarkImgBtn = (ImageButton) findViewById(R.id.bookmarkImgBtn);
		bookmarkImgBtn.setOnClickListener(handlebookmarkImgBtn);
		db = new DBAdapter(this);
		imgURLListforDB = new ArrayList<String>();
		
		// ---------------------------------
		// Configure Previous Image Button
		// ---------------------------------
		prevImgBtn = (ImageButton) findViewById(R.id.prevImgBtn);
		prevImgBtn.setOnClickListener(handleprevImgBtn);
		lolCatsCacheDir = this.getCacheDir();
		lolCatsSaveDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
		currDispImage = imageCeiling = 0;
		eraseDir(lolCatsCacheDir); //clear all files in the cache
		lolCatsCacheDir.mkdirs(); //re-create the top-level cache folder that was deleted by eraseDir
		
		// ----------------------------------
		// Configure Image display area
		// ----------------------------------
		imgView = (ImageView) findViewById(R.id.imgShow);
		
		// --------------------------------------------------------
		// Configure startup images area with fling support
		// --------------------------------------------------------
		startupImageFlipper = (ViewFlipper) findViewById(R.id.startupImageFlipper);
		imageSwitcher = (ImageSwitcher) findViewById(R.id.imageSwitcher);
		slideLeftIn = AnimationUtils.loadAnimation(this, R.anim.slide_left_in);
		slideRightIn = AnimationUtils.loadAnimation(this, R.anim.slide_right_in);
		slideLeftOut = AnimationUtils.loadAnimation(this, R.anim.slide_left_out);
		slideRightOut = AnimationUtils.loadAnimation(this, R.anim.slide_right_out);
		flingListenerClass flingListener = new flingListenerClass(); //listener callback for GestureDetector
		flingDetector = new GestureDetector(LolAnimalsActivity.this, flingListener);
	}
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * CHECK NETWORK CONNECTIVITY
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	private boolean checkNetworkAvailable(Context ctx) {
		ConnectivityManager connManService = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo wifiNetworkInfo = connManService.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		NetworkInfo mobileNetworkInfo = connManService.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		if((!wifiNetworkInfo.isConnected())&& (!mobileNetworkInfo.isConnected())){
			Log.d(TAG,"Wifi/Mobile data not connected");
			AlertDialog.Builder alertBuilder = new AlertDialog.Builder(LolAnimalsActivity.this);
			alertBuilder.setTitle("No network connection")
						.setMessage("Please check your network connection.\n" +
								"A 3G or WiFi connection is recommended for this application.")
						.setIcon(R.drawable.nonetwork)
			       		.setCancelable(false)
			       		.setNeutralButton("OK", new DialogInterface.OnClickListener() {
			       			public void onClick(DialogInterface dialog, int id) {
			                LolAnimalsActivity.this.finish();
			       			}
			       		});
			AlertDialog alert = alertBuilder.create();
			alert.show();
			return false;
		} else {
			return true;
		}
	}

	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * OPTIONS MENU
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	@Override
	public boolean onCreateOptionsMenu(Menu m){
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, m);
		return true;
	}
	
	//---------------------------------------------
	// Handle options menu selections
	//---------------------------------------------
	public boolean onOptionsItemSelected(MenuItem item){
		switch(item.getItemId()){
		case R.id.optionsMenuQuit:
			this.finish();
			return true;
		case R.id.optionsBookmarkMode:
			Intent intent = new Intent(LolAnimalsActivity.this,bookMarkActivity.class);
			startActivityForResult(intent, BOOKMARKACTIVITY_IDENTIFIER);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private void eraseDir(File fileToDelete) {
		if(fileToDelete != null && fileToDelete.isDirectory()){
			String[] files = fileToDelete.list();
			for(int  i = 0; i < files.length; i++){
				eraseDir(new File(fileToDelete, files[i]));
			}
		}
		fileToDelete.delete();
	}

	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * * 
	 * BUTTONS AND BUTTON HANDLERS
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/

	//---------------------------------------------
	// Handler for Fetch Image Button Clicks
	//---------------------------------------------
	OnClickListener handleNextImgBtn = new OnClickListener() {
		public void onClick(View view) {
			if(nextImgBtn.isHapticFeedbackEnabled()){
				nextImgBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY,
						android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); //notify the user that a successful tap has been registered
			}
			//the following block is only useful for the first tap on the next image button IF the user has
			//not already touched the screen
			if(startupImageFlipper.isFlipping()){
				startupImageFlipper.stopFlipping();	
				startupImageFlipper.setDisplayedChild(1); //index 0 is equivalent to the first child view
			}
			displayNewImage();
		}
	};

	// ---------------------------------
	// Handler for Save Image Button Clicks
	// ---------------------------------
	OnClickListener handleSaveImgBtn = new OnClickListener() {
		public void onClick(View view) {
			if(saveImgBtn.isHapticFeedbackEnabled()){
				saveImgBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY,
						android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); //notify the user that a successful tap has been registered
			}
			//the following block is only useful for the first tap on the next image button IF the user has
			//not already touched the screen
			if(startupImageFlipper.isFlipping()){
				startupImageFlipper.stopFlipping();	
				startupImageFlipper.setDisplayedChild(1); //index 0 is equivalent to the first child view
			}
			if(imageCeiling > 0){
				saveImage(lolCatsSaveDir, SAVE_MODE);
			} else {
				Toast.makeText(LolAnimalsActivity.this, "No image displayed", Toast.LENGTH_SHORT).show();
			}
			
		}
	};
	
	// --------------------------------------------------
	// Handler for Bookmark Image Button Clicks
	// --------------------------------------------------
	OnClickListener handlebookmarkImgBtn = new OnClickListener() {
		public void onClick(View view) {
			if(bookmarkImgBtn.isHapticFeedbackEnabled()){
				bookmarkImgBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY,
						android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); //notify the user that a successful tap has been registered
			}
			//the following block is only useful for the first tap on the next image button IF the user has
			//not already touched the screen
			if(startupImageFlipper.isFlipping()){
				startupImageFlipper.stopFlipping();	
				startupImageFlipper.setDisplayedChild(1); //index 0 is equivalent to the first child view
			}
			if(imageCeiling > 0){
				if(currDispImage != 0){
					db.open();
					db.insertURL(imgURLListforDB.get(currDispImage-1));
					Log.d(TAG,"Adding to DB: " + imgURLListforDB.get(currDispImage-1));
					db.close();
					Toast.makeText(LolAnimalsActivity.this,"Image bookmarked", Toast.LENGTH_SHORT).show();
				}
			} else {
				Toast.makeText(LolAnimalsActivity.this, "No image displayed", Toast.LENGTH_SHORT).show();
			}
		}
	};
	
	//---------------------------------------------
	// Handler for Previous Image Button Clicks
	//---------------------------------------------
	OnClickListener handleprevImgBtn = new OnClickListener() {
		public void onClick(View view) {
			if(prevImgBtn.isHapticFeedbackEnabled()){
				prevImgBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY,
						android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); //notify the user that a successful tap has been registered
			}
			//the following block is only useful for the first tap on the next image button IF the user has
			//not already touched the screen
			if(startupImageFlipper.isFlipping()){
				startupImageFlipper.stopFlipping();	
				startupImageFlipper.setDisplayedChild(1); //index 0 is equivalent to the first child view
			}
			if(imageCeiling > 0){
				displayOldImage();
			} else {
				Toast.makeText(LolAnimalsActivity.this, "No previous images to show", Toast.LENGTH_SHORT).show();
			}
		}
	};
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * IMAGE DISPLAY FUNCTIONS
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	private void displayNewImage(){
		if(imageCeiling == currDispImage){ //the cache images have caught up to the displayed images and we can
																			   //now fetch new images
			if(imgQueue.isEmpty()){ //handle empty image queue
				Log.d(TAG,"No images in List!");
				showDialog(PROGRESS_DIALOG_ID);
			} else { //this else statement is IMPORTANT. Without it, the main (UI) thread will not wait for the thread spawned
				 	    //by showDialog -> onCreateDialog to finish. Instead, nextPhotoProgression will be called before the
					    //image queue is ready and the application will crash. However, this decision means that the very
				 	    //first time the queue is populated (and, indeed, every time the queue is found empty),
					    //nextPhotoProgression will not be called (since only the code in the if block will be run). This means
					    //that although the queue has been updated behind-the-scenes, a new image will not be shown to the
					    //user. To see it, the user will have to click the next image button again. This creates an unsatisfactory
					    //experience. Hence, we add nextPhotoProgression to the 'empty image queue' logic flow as well. This
					    //has been done at the end of the background thread handler. Thus, after the background thread
					    //finishes filling up the queue and after the thread handler has dismissed the progress dialog, it will
					    //call nextPhotoProgression and a new image will be drawn for the user.
				showDialog(BUSY_DRAWING_ID);
			}
		} else { //we still have cached images to display
			File dispImgFile = new File(lolCatsCacheDir, ++currDispImage +".jpg");
			imgBitMap = BitmapFactory.decodeFile(dispImgFile.toString());
			imgView.setImageBitmap(imgBitMap);
		}
	}

	private void displayOldImage(){
		if(currDispImage > 1){
			File dispImgFile = new File(lolCatsCacheDir, --currDispImage +".jpg");
			imgBitMap = BitmapFactory.decodeFile(dispImgFile.toString());
			if(imgBitMap != null){
				imgView.setImageBitmap(imgBitMap);
			} else { //one possible reason for the bitmap being invalid is that the system
						 //may have chosen to remove files from this application's cache to free
						 //up system resources. In such a case, continuing to click the previous
						 //image button will have the following effect:
						 //1. the same image will keep on being displayed to the user
						 //2. a Toast informing the user that there are no more images in history
						 //    will be displayed
						 //NB: The reason currDispImage is not reset to 0 (similar to what is done
						 //four lines below) is that there may still be images in the cache (unless
						 // the system deleted ALL the cached files) and the user
						 //may wish to look at them before going on to fetch new images
				currDispImage++;
				Toast.makeText(LolAnimalsActivity.this, "No More Images in History", Toast.LENGTH_SHORT).show();
			}
		} else{ //no previous images to display
			currDispImage = 0;
			imgView.setImageResource(R.drawable.startbackground1);
			Toast.makeText(LolAnimalsActivity.this, "No More Images in History", Toast.LENGTH_SHORT).show();
			Log.d(TAG,"No previous images to display");
		}
	}
	
	private boolean saveImage(File saveDir, int Mode){
		switch(Mode){
		case CACHE_MODE:
			if (saveDir != null && saveDir.isDirectory()){
				try{
					File saveImgFile = new File(saveDir, ++imageCeiling +".jpg");
					currDispImage = imageCeiling;
					FileOutputStream imgSaveStream = new FileOutputStream(saveImgFile);
					BufferedOutputStream bufferedImgSaveStream = new BufferedOutputStream(imgSaveStream);
					imgBitMap.compress(CompressFormat.JPEG,75,bufferedImgSaveStream);
					bufferedImgSaveStream.flush();
					bufferedImgSaveStream.close();	
					imgURLListforDB.add(currDispImage-1, imgURL.toString());
					Log.d(TAG, "Image cached to " + saveImgFile.toString());
				} catch(Exception e){
					Log.d(TAG,"Problem caching  image");
				}
			}
			return true;
		case SAVE_MODE:
			if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
				try{
					saveDir.mkdirs();
					File saveImgFile = new File(saveDir, uniqueFileName.format(new Date()).toString()+".jpg");
					Log.d(TAG,"Directory: " + saveDir.toString());
					Log.d(TAG,"Filename: " + saveImgFile.toString());
					FileOutputStream imgSaveStream = new FileOutputStream(saveImgFile);
					BufferedOutputStream bufferedImgSaveStream = new BufferedOutputStream(imgSaveStream);
					imgBitMap.compress(CompressFormat.JPEG,75,bufferedImgSaveStream); //the AndroidManifest must give the application the
																																					//requisite permissions for this to work
					bufferedImgSaveStream.flush();
					bufferedImgSaveStream.close();	
					Toast.makeText(this, "Image Saved to " + saveDir.toString(), Toast.LENGTH_SHORT).show();
				} catch(Exception e){
					Log.d(TAG,"Problem saving image");
				}
			} else{
			    Log.d(TAG,"Media is not writable");
			}
			return true;
		default:
			return true;
		}
	}
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * DIALOG HANDLERS
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	protected Dialog onCreateDialog(int ID){ //called when dialog is created in showDialog function
		switch(ID){
		case PROGRESS_DIALOG_ID:
			//create a new dialog
			popimgQueueThreadProgressDialog = new ProgressDialog(LolAnimalsActivity.this);
			popimgQueueThreadProgressDialog.setMessage("Fetching images from the Internet...");
			//create a new thread to go with the progress dialog
			//this thread will attempt to fetch URLs of relevant images
			popimgQueueThread = new ProgressThread(popimgQueueThreadHandler);
			popimgQueueThread.start();
			return popimgQueueThreadProgressDialog;
		case BUSY_DRAWING_ID:
			busyDrawingThreadProgressDialog = new ProgressDialog(LolAnimalsActivity.this);
			busyDrawingThreadProgressDialog.setMessage("Drawing image...");
			busyDrawingThread = new BusyDrawingThread(busyDrawingThreadHandler);
			busyDrawingThread.start();
			return busyDrawingThreadProgressDialog;
		default:
			return null;
		}
	}
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * THREAD AND THREAD HANDLERS
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	// --------------------------------------
	// Thread and handler for progress dialog
	// --------------------------------------
	private class ProgressThread extends Thread{
		Handler threadHandler;

		public ProgressThread(Handler h){
			threadHandler = h;
		}
		
		public void run(){
				imgQueue = populateimgQueue(imgQueue);
				threadHandler.sendEmptyMessage(0);
		}
	}
	
	final Handler popimgQueueThreadHandler = new Handler() {
		@Override
		public void handleMessage(Message msg){
			dismissDialog(PROGRESS_DIALOG_ID);	  //the progress dialog can be dismissed
			removeDialog(PROGRESS_DIALOG_ID);
			showDialog(BUSY_DRAWING_ID);
		}
	};

	// ---------------------------------------------
	// Thread and handler for 'Drawing Image' dialog
	// ---------------------------------------------
	private class BusyDrawingThread extends Thread{
		Handler threadHandler;

		public BusyDrawingThread(Handler h){
			threadHandler = h;
		}
		
		public void run(){
			try{
				imgURL = new URL(imgQueue.remove());
				Log.d(TAG, "Drawing Image URL: " + imgURL); // Debugging only
				imgStream = (InputStream) (imgURL.getContent());
				bufferedimgStream = new BufferedInputStream(imgStream);
				threadHandler.sendEmptyMessage(0);
			} catch (MalformedURLException e1) { // invalid URL
				imgStream = null;
				bufferedimgStream = null;
				Log.d(TAG, "URL Malformed"); // Debugging only
			} catch (IOException e) { // HTTP connection problems
				imgStream = null;
				bufferedimgStream = null;
				Log.d(TAG, "HTTP Connection Error"); // Debugging only
			}
		}
	}
	
	final Handler busyDrawingThreadHandler = new Handler() {
		@Override
		public void handleMessage(Message msg){
			dismissDialog(BUSY_DRAWING_ID);	  //the busy drawing dialog can be dismissed
			removeDialog(BUSY_DRAWING_ID);
			if(bufferedimgStream != null){
				imgBitMap = BitmapFactory.decodeStream(bufferedimgStream);
				startupImageFlipper.removeAllViews();
				imgView.setImageBitmap(imgBitMap);
				saveImage(lolCatsCacheDir, CACHE_MODE); //cache the image currently being shown
			}
		}
	};
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * THREAD SUPPORT FUNCTIONS
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	// ---------------------
	// HTML Parsing function
	// ---------------------
	private Queue<String> populateimgQueue(Queue<String> validImgURLQueue) {
		try {
			Source source = new Source(new URL(
					"http://icanhascheezburger.com/tag/caption/page/" + pageNum));
			Log.d(TAG,"Source page: " + "http://icanhascheezburger.com/tag/caption/page/" + pageNum++);
			List<Element> imgList = source
					.getAllElements(HTMLElementName.IMG);
			for (Element img : imgList) {
				String imgSrc = img.getAttributeValue("src");
				if (imgSrc.matches(".*icanhascheezburger.*wordpress.*[a-zA-Z]$")) {
					validImgURLQueue.offer(imgSrc.toString());
					Log.d(TAG, "Image src: " + imgSrc);
				}
			}
		} catch (MalformedURLException e) {
			Log.d(TAG, "Malformed URL");
			e.printStackTrace();
		} catch (IOException e) {
			Log.d(TAG, "HTML Connection Error");
			e.printStackTrace();
		}
		return validImgURLQueue;
	}
	
	/**********************************************
	 * * * * * * * * * * * * * * * * * * * * * * *
	 * GESTURE HANDLERS
	 * * * * * * * * * * * * * * * * * * * * * * *
	**********************************************/
	class flingListenerClass extends SimpleOnGestureListener {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
			// make sure that unintentional taps are discarded
			// (works for both left to right and right to left swipes)
			try{
				if(Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
				return false;
			// right to left swipe
			if(((e1.getX() - e2.getX()) >SWIPE_MIN_DISTANCE) && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY){
				if(imageSwitcher.isHapticFeedbackEnabled()){ 
					imageSwitcher.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY,
							android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); //notify the user that a successful swipe has been registered
				}
				imageSwitcher.setInAnimation(slideLeftIn);
				imageSwitcher.setOutAnimation(slideLeftOut);
				displayNewImage();
				Log.d(TAG,"Right to left swipe detected");
			}
			// left to right swipe
			else if(((e2.getX() - e1.getX()) >SWIPE_MIN_DISTANCE) && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY){
				if(imageSwitcher.isHapticFeedbackEnabled()){ 
					imageSwitcher.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY,
							android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING); //notify the user that a successful swipe has been registered
				}
				imageSwitcher.setInAnimation(slideRightIn);
				imageSwitcher.setOutAnimation(slideRightOut);
				if(imageCeiling > 0){
					displayOldImage();
				} else {
					Toast.makeText(LolAnimalsActivity.this, "No previous images to show", Toast.LENGTH_SHORT).show();
				}
				Log.d(TAG,"Left to right swipe detected");
			}
			return true;
			} catch (Exception e){
				return false;
			}
		}
	}
	
	// onTouchEvent will be called every time a motion event
	// is detected in this activity. We need to pass the event
	// on to the GestureDetector flingDetector's onTouchEvent which
	// will then call flingListenerClass's onFling callback function
	// Since the buttons are not set focusable in touch mode, they will
	// not be affected by swipes
	@Override
	public boolean onTouchEvent(MotionEvent e){
		if(startupImageFlipper.isFlipping()){
			startupImageFlipper.stopFlipping();	
			startupImageFlipper.setDisplayedChild(1); //index 0 is equivalent to the first child view
		}
		if(flingDetector.onTouchEvent(e)) return true;
		else return false;
	}

	@Override
	public void onRestart(){ //called whenever we come back from bookMarkActivity
		// --------------------------
		// Check network connectivity
		// --------------------------
		if(checkNetworkAvailable(LolAnimalsActivity.this)){
			Toast greeting = Toast.makeText(this,"Displaying All Images", Toast.LENGTH_LONG);
			greeting.setGravity(Gravity.CENTER, 0, 0);
			greeting.show();
		}
		super.onRestart();
	}
	
	// ----------------------------------------------------------------------------------------------------
	// If bookMarkActivity indicated that the network connection was down, we exit the application
	// directly, rather than displaying another 'Network connection error' dialog from LolAnimalsActivity's
	// onRestart() function
	// An example useflow that could make this happen is:
	// 1. the user opens up the application
	// 2. changes to bookMarkActivity
	// 3. minimizes the application (returns to the home screen via the 'Home' button)
	// 4. turnsd off the Wifi
	// 5. tries to resume the application from the 'Recent Applications' dialog (long press on the 'Home'
	//    button)
	// ----------------------------------------------------------------------------------------------------
	@Override
	protected void onActivityResult(int requestcode, int resultcode, Intent intent){
		switch(requestcode){
		case BOOKMARKACTIVITY_IDENTIFIER:
			if(resultcode == RESULT_OK){ //this result code indicates no network connectivity
								 		 //was detected by bookMarkActivity
				LolAnimalsActivity.this.finish();
			}
		default:
			break;
		}
	}

}

/* TO-DO
A: Add Menu Options to:
1. PENDING: Disable haptic feedback for users who don't like it
2. PENDING: Give user an option of where to save the images.
3. PENDING: Set auto progress to next image
4. PENDING: Share options (that will also include source URL of the page the image was on; this could be helpful in a sharing by email scenario)
5. PENDING: Rate app on Market
6. DONE: Gracefully exit program
	-- Superfluous but an option has been added to the Options menu nevertheless
7. DONE: Set fullscreen (hide notification bar)
	-- No need since fullscreen is now set by default (because images look better without the distraction of the notification bar)

B: Add ContextMenu Option to:
1. Share options (that will also include source URL of the page image was on)
2. Set image as favorite
3. See A.4

C: UI Enhancements:
let user choose target directory for saves.
1. DONE: Add Context support
 	-- IS THIS REALLY REQUIRED IF ROTATE HAS BEEN DISABLED? Need to look into the documentation for answers.
 	-- UPDATE: The original intent of this bullet point has been forgotten. :-P Anyway, the application works fine now.
2. DONE: Add network connectivity check on startup. Inform user if no Internet connection detected and an option to:
	a. Shut down app/Try again later. MUST DECIDE ON "Shut down" AS finish() SEEMS SUPERFLUOUS IN ANDROID.
	b. Go to Settings/Wifi and Network
	-- UPDATE: only option a has been added. Option b is not necessary.
3. DONE: Add functionality, button to set image as favorite
	-- This should give the user a list of select images that he can cycle through (with an Internet connection, of course) To
	-- implement, we can save the image URL since it's already available.
	-- UPDATE: an SQLite database has been implemented that saves the URLs of favorited images. 
	-- This will also necessitate a change in start-up options. The user should be able to select between two different modes
	-- of operation:
	-- 	a. cycle through all images on the website
	-- 	b. cycle only through images previously marked as favorites. If selecting this option, the user should also be given
	-- 		the option of un-favoriting an image (either through the context menu or using a dedicated button).
	-- Having two modes of operations means that the user should be given an option via an OptionsMenu to return to
	-- the App Home screen at any time in case he wants to switch modes on the fly.
	-- UPDATE: Done.
 4. DONE: Add Functionality, button and swipe support to move to previous image
 5. DONE: Is there a faster way to draw images than dealing with an ImageStream?
 	-- Not exactly. However, I have added a buffered input stream to speed things up and used a Bitmap object rather
 	-- a drawable (although this is not really relevant to this point). What has really improved things from the
 	-- user's POV is a new background thread to fetch the image from the server. This means that the user can now
 	-- be shown a progress dialog (albeit an indeterminate one) while the HTTP transfer is being made. This reassures
 	-- the user that the application has not crashed or become unresponsive and also does away with the uncomfortable
 	-- lag in response seen in the previous version of the program
 	-- had before this change was implemented
 6. DONE: Add functionality, button to save Image
 7. DONE: Give all Buttons custom icons (probably have to add new drawables)
 	-- Also added a cool glow effect when clicked. However, some buttons still remain (see Nos. 1, 2, 3)
 8. DONE: Make all buttons (with custom icons added) now hover on image background
 	-- Done using Framelayout.
 9. DONE: Add Swipe support
 	-- Done only partially. See No. 3.
 10. DONE: Add cute cat icon for launcher
 11. DONE: Display progress dialog when images are being fetched
 	-- Done using a background thread that doesn't block the UI.
 12. DONE: Check how app performs on screen rotate and whether it retains state. Also, what about the image size and how it is displayed?
	-- Disabled screen rotate since images do not scale well in landscape mode
*/