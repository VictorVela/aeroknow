package biz.eventually.atpl.ui.questions

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.support.v4.content.ContextCompat
import android.support.v7.widget.CardView
import android.view.*
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import biz.eventually.atpl.AtplApplication
import biz.eventually.atpl.BuildConfig
import biz.eventually.atpl.R
import biz.eventually.atpl.common.IntentIdentifier
import biz.eventually.atpl.data.NetworkStatus
import biz.eventually.atpl.data.db.Question
import biz.eventually.atpl.data.db.Topic
import biz.eventually.atpl.ui.BaseComponentActivity
import biz.eventually.atpl.ui.ViewModelFactory
import biz.eventually.atpl.utils.Prefields
import biz.eventually.atpl.utils.Prefields.PREF_TIMER_NBR
import biz.eventually.atpl.utils.prefsGetValue
import cn.pedant.SweetAlert.SweetAlertDialog
import com.github.pwittchen.swipe.library.Swipe
import com.github.pwittchen.swipe.library.SwipeListener
import com.squareup.picasso.Callback
import com.squareup.picasso.NetworkPolicy
import com.squareup.picasso.Picasso
import com.tapadoo.alerter.Alerter
import kotlinx.android.synthetic.main.activity_questions.*
import org.jetbrains.anko.share
import javax.inject.Inject


class QuestionsActivity : BaseComponentActivity() {

    private var mTopic: Topic? = null
    private var mData = QuestionState(Question(-1, -1, "", ""), 0, 0)

    private var mShowAnswer = false

    private var transparentColor: Int = 0x00000000
    private var mTimer: CountDownTimer? = null
    private var mWithCountDown = true

    private var isLight: Boolean = true

    /**
     * Flag to determined if any question as been done with the following stats request
     * to refresh the Subject screen while going back
     */
    private var mHadChange = false

    private var mHasToken = false

    private var mMenuShuffle: MenuItem? = null
    private var mMenuShare: MenuItem? = null

    private var mSwipe: Swipe? = null

    @Inject
    lateinit var questionViewModelFactory: ViewModelFactory<QuestionRepository>

    private lateinit var mViewModel: QuestionViewModel

    // answer ticked results for stat
    private val mStatistic = mutableMapOf<Long, Int>()

    private val mMime = "text/html"
    private val mEncoding = "utf-8"

    private lateinit var mQuestionCardView: List<CardView>

    private var mDelay: Long = 60000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_questions)

        AtplApplication.component.inject(this)

        mQuestionCardView = listOf<CardView>(question_answer_1, question_answer_2, question_answer_3, question_answer_4)
        mWithCountDown = prefsGetValue(Prefields.PREF_TIMER_ENABLE, true)

        /***
         * Data
         */
        val topicId = intent.extras.getLong(IntentIdentifier.TOPIC)
        val startFirst = intent.extras.getBoolean(IntentIdentifier.TOPIC_STARRED, false)

        mViewModel = ViewModelProviders.of(this, questionViewModelFactory).get(QuestionViewModel::class.java)
        mViewModel.launchTest(topicId, startFirst)

        mViewModel.question.observe(this, Observer<QuestionState> {
            it?.let {
                mData = it
                displayQuestion()
            }
        })

        mViewModel.networkStatus.observe(this, Observer<NetworkStatus> {
            when (it) {
                NetworkStatus.LOADING -> question_rotate.start()
                NetworkStatus.SUCCESS -> question_rotate.stop()
                else -> run({})
            }
        })

        mTopic?.apply {
            mViewModel.launchTest(idWeb, startFirst)
            supportActionBar?.title = name
        }

        // Listeners
        initListeners()

        question_label.setBackgroundColor(Color.TRANSPARENT)
        question_label.settings.javaScriptEnabled = false


        // countdown mDelay
        val secondsStr = prefsGetValue(PREF_TIMER_NBR, "60")
        if (secondsStr.isNotEmpty()) {
            mDelay = secondsStr.toLong() * 1000
        }
    }

    override fun onDestroy() {
        mTimer?.cancel()
        mTimer = null
        super.onDestroy()
    }

    private fun initListeners() {

        // initiate onClick on all Question CardView
        mQuestionCardView.forEachIndexed { index, cardView -> cardView.setOnClickListener({ onAnswerClick(it, index) }) }

        listOf<CheckBox>(
                question_answer_1_rdo,
                question_answer_2_rdo,
                question_answer_3_rdo,
                question_answer_4_rdo
        ).forEachIndexed { index, checkbox ->
            checkbox.setOnClickListener { onAnswerClick(mQuestionCardView[index], index) }
        }

        question_previous.setOnClickListener {
            mMenuShare?.isVisible = false

            mViewModel.previous()?.let {
                mStatistic[mData.question.idWeb] = if (it) 1 else 0
                mMenuShare?.isVisible = true
            } ?: run { mMenuShare?.isVisible = true }
        }

        question_next.setOnClickListener {
            mMenuShare?.isVisible = false

            mViewModel.next()?.let {
                mStatistic[mData.question.idWeb] = if (it) 1 else 0
                mMenuShare?.isVisible = true
            } ?: run { mMenuShare?.isVisible = false }
        }

        question_last.setOnClickListener {
            mMenuShare?.isVisible = false

            mViewModel.next()?.let {
                mStatistic[mData.question.idWeb] = if (it) 1 else 0
                mMenuShare?.isVisible = true

                // show stats of result
                showLocalStats()
            } ?: run { mMenuShare?.isVisible = false }

            it.visibility = View.GONE
        }

        mSwipe = Swipe()
        mSwipe?.setListener(
                object : SwipeListener {
                    override fun onSwipedLeft(event: MotionEvent) {
                        question_next.performClick()
                    }

                    override fun onSwipedRight(event: MotionEvent) {
                        question_previous.performClick()
                    }

                    override fun onSwipingLeft(event: MotionEvent) {}
                    override fun onSwipingRight(event: MotionEvent) {}
                    override fun onSwipingUp(event: MotionEvent) {}
                    override fun onSwipedUp(event: MotionEvent) {}
                    override fun onSwipingDown(event: MotionEvent) {}
                    override fun onSwipedDown(event: MotionEvent) {}
                })
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        mSwipe?.dispatchTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_shuffle, menu)

        mMenuShuffle = menu?.findItem(R.id.action_shuffle)
        mMenuShare = menu?.findItem(R.id.action_share)

        mMenuShuffle?.isVisible = false
        mMenuShare?.isVisible = false

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_shuffle -> {
                shuffleQuestions()
                true
            }
            R.id.action_share -> {
                val shareTxt = constructShareText()
                if (!shareTxt.isEmpty()) {
                    share(shareTxt, mTopic?.name ?: "")
                }

                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun constructShareText(): String {

        val txt = StringBuilder()

        txt.append(mData.question.label)
                .append("\n")
                .append("\n")

        mData.question.answers.forEach { answer ->
            val line = if (answer.good) "+" else "-"
            txt.append("$line ${answer.value}")
            txt.append("\n")
        }

        return txt.toString()
    }

    override fun onBackPressed() {

        if (mHadChange) {
            setResult(Activity.RESULT_OK, Intent())
        } else {
            setResult(Activity.RESULT_CANCELED, Intent())
        }

        super.onBackPressed()
    }

    private fun showLocalStats() {

        val result = mStatistic.entries.filter { it.value > 0 }.sumBy { it.value }.toDouble()
        val percent = if (result == 0.0) 0 else ((result / mStatistic.size.toDouble()) * 100).toInt()

        var title = getString(R.string.result_title_good)
        val message = getString(R.string.result_value, percent)
        var warningType = SweetAlertDialog.SUCCESS_TYPE

        when {
            percent < 75 -> {
                title = getString(R.string.result_title_bad)
                warningType = SweetAlertDialog.ERROR_TYPE
            }
            percent < 85 -> {
                title = getString(R.string.result_title_warning)
                warningType = SweetAlertDialog.WARNING_TYPE
            }
            else -> SweetAlertDialog.SUCCESS_TYPE
        }

        SweetAlertDialog(this, warningType)
                .setTitleText(title)
                .setContentText(message)
                .setConfirmClickListener({ finish() })
                .show()
    }

    private fun shuffleQuestions() {
        mViewModel.shuffle()
    }

    private fun displayQuestion() {

        mTimer?.cancel()

        if (question_imgs.childCount > 0) {
            question_imgs.removeAllViews()
        }

        mShowAnswer = false

        initAnswerCardDisplay()
        resetCheckbox()

        mData.question.apply {
            // reset the background, if ever it has turned to yellow
            question_label.setBackgroundColor(transparentColor)
            question_label.loadData(label, mMime, mEncoding)

            val questionAnswerTextView = listOf<TextView>(
                    question_answer_1_text,
                    question_answer_2_text,
                    question_answer_3_text,
                    question_answer_4_text
            )

            for (i in 0 until answers.count()) {
                questionAnswerTextView[i].text = answers[i].value
            }

            imgList.forEach { img ->
                val imgContainer = ImageView(applicationContext)
                // https://www.codeday.top/2017/07/31/31373.html
                Picasso.with(applicationContext)
                        .load(BuildConfig.API_ATPL_IMG + img)
                        .networkPolicy(NetworkPolicy.OFFLINE)
                        .into(imgContainer, object : Callback {
                            override fun onSuccess() {}

                            override fun onError() {
                                Picasso.with(applicationContext)
                                        .load(BuildConfig.API_ATPL_IMG + img)
                                        .into(imgContainer)
                            }
                        })

                question_imgs.addView(imgContainer)
            }

            if (mWithCountDown) launchCountDown()
        }

        val currentIndex = mData.index
        val count = mData.size
        if (count > -1) {
            question_range.text = "${currentIndex + 1} / $count"

            question_next.visibility = if (currentIndex < count - 1) View.VISIBLE else View.GONE
            question_last.visibility = if (currentIndex == count - 1) View.VISIBLE else View.GONE
        }

        question_previous.visibility = if (currentIndex > 0) View.VISIBLE else View.GONE

        mMenuShare?.isVisible = mData.question.idWeb > -1
    }

    private fun launchCountDown() {

        question_time.setTextColor(ContextCompat.getColor(applicationContext, R.color.colorGrey))

        mTimer = object : CountDownTimer(mDelay, 1000) {
            override fun onFinish() {
                question_time.text = ""
                question_label.setBackgroundColor(ContextCompat.getColor(applicationContext, R.color.colorAccent))
            }

            override fun onTick(millisUntilFinished: Long) {

                question_time.text = "${millisUntilFinished / 1000}"

                if (millisUntilFinished < 10000) {
                    when (isLight) {
                        true -> {
                            question_label.setBackgroundColor(ContextCompat.getColor(applicationContext, R.color.colorGreyLight))
                            question_time.setTextColor(ContextCompat.getColor(applicationContext, R.color.colorPrimaryDark))
                        }
                        false -> {
                            question_label.setBackgroundColor(ContextCompat.getColor(applicationContext, R.color.colorLight))
                            question_time.setTextColor(ContextCompat.getColor(applicationContext, R.color.colorAccent))
                        }
                    }
                    isLight = !isLight
                }
            }
        }.start()
    }

    private fun onSavingError() {
        Alerter.create(this)
                .setTitle(getString(R.string.dialog_title_error))
                .setText(getString(R.string.question_focus_error))
                .setBackgroundColorRes(R.color.colorSecondary)
                .show()
    }

    private fun initAnswerCardDisplay() {
        for (i in 0..3) {
            mQuestionCardView[i].setBackgroundColor(ContextCompat.getColor(applicationContext, R.color.cardview_light_background))
        }
    }

    private fun onAnswerClick(view: View, index: Int) {

        mShowAnswer = !mShowAnswer
        mViewModel.tickAnswer(if (mShowAnswer) index else -1)

        if (mShowAnswer) {
            resetCheckbox()
            checkOneBox(view as CardView, true)
            showAnswer()
            mTimer?.cancel()
        } else {
            initAnswerCardDisplay()
            resetCheckbox()
        }

    }

    private fun resetCheckbox() {
        for (i in 0..3) {
            checkOneBox(mQuestionCardView[i], false)
        }
    }

    private fun checkOneBox(card: CardView, check: Boolean) {

        // get the LinearLayout inside the CardView
        val group = (card as ViewGroup).getChildAt(0) as ViewGroup
        var box: CheckBox? = null

        (0 until group.childCount).forEach { i ->
            if (group.getChildAt(i) is CheckBox) {
                box = group.getChildAt(i) as CheckBox
            }
        }

        box?.let { it.isChecked = check }
    }

    private fun showAnswer() {
        mData.question.answers.let {
            (0 until it.count())
                    .filter { i -> it[i].good }
                    .forEach { i ->
                        mQuestionCardView[i].background = if (it[i].good)
                            ContextCompat.getDrawable(applicationContext, R.drawable.answer_right)
                        else
                            ContextCompat.getDrawable(applicationContext, R.drawable.answer_wrong)
                    }
        }
    }
}
