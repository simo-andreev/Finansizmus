package com.vladimircvetanov.smartfinance;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.github.mikephil.charting.utils.ViewPortHandler;
import com.szugyi.circlemenu.view.CircleLayout;
import com.vladimircvetanov.smartfinance.db.DBAdapter;
import com.vladimircvetanov.smartfinance.model.CategoryExpense;

import java.text.DecimalFormat;
import java.util.ArrayList;

import static com.vladimircvetanov.smartfinance.model.Manager.getLoggedUser;


public class DiagramFragment extends Fragment {

    private FrameLayout frameLayout;
    private PieChart pieChart;
    private ArrayList<PieEntry> entries;
    private PieData pieData;
    private PieDataSet pieDataSet;
    private Button totalSumButton;

    //Temp
    private EditText addValue;
    private Button addButton;
    private CircleLayout circleLayout;
    private DBAdapter adapter;

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_diagram, container, false);

        circleLayout = (CircleLayout) rootView.findViewById(R.id.frame);
        pieChart = (PieChart) rootView.findViewById(R.id.pie_chart);
        entries = new ArrayList<>();
        pieDataSet = new PieDataSet(entries, "");
        totalSumButton = (Button) rootView.findViewById(R.id.total_sum_btn);
        adapter = DBAdapter.getInstance(getActivity());

        /*Temp button
        addButton = (Button) rootView.findViewById(R.id.add_value_btn);
        addValue = (EditText) rootView.findViewById(R.id.add_value);

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!addValue.getText().toString().isEmpty() && Integer.parseInt(addValue.getText().toString()) > 0) {
                    addEntry(Integer.parseInt(addValue.getText().toString()));
                }
            }
        }); */

        drawDiagram();
        drawFavouriteIcons();

        final LinearLayout layout = (LinearLayout) rootView.findViewById(R.id.report_layout);
        final float[] startY = new float[1];
        final float[] translationY = new float[1];
        layout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        startY[0] = event.getY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float newY = event.getY();
                        float deltaY = startY[0] - newY;
                        translationY[0] = v.getTranslationY();
                        translationY[0] -= deltaY;
                        if (translationY[0] < 0)
                            translationY[0] = 0;
                        if (translationY[0] >= v.getHeight()-150)
                            translationY[0] = v.getHeight()-150;
                        v.setTranslationY(translationY[0]);
                        return true;
                    default:
                        Interpolator interpolator = new AccelerateDecelerateInterpolator();
                        v.animate().setInterpolator(interpolator).translationY(translationY[0] < v.getHeight()/2 ? 0 : v.getHeight()-totalSumButton.getHeight());
                        return v.onTouchEvent(event);
                }
            }
        });

        totalSumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (layout.getTranslationY() != 0){
                    Interpolator interpolator = new AccelerateDecelerateInterpolator();
                    layout.animate().setInterpolator(interpolator).translationY(0);
                }
            }
        });

        return rootView;
    }

    private void addEntry(float entrySum) {
        if (entries.size() == 1) {
            entries.clear();
            entries.add(new PieEntry(0));
        }
        PieEntry entry = new PieEntry(entrySum);
        entries.add(entry);

        pieChart.setData(pieData);
        pieChart.invalidate();

        getLoggedUser().totalSum += entrySum;
        totalSumButton.setText(String.valueOf(getLoggedUser().totalSum));
    }

    void drawDiagram() {

        if (entries.isEmpty()) {
            entries.add(new PieEntry(100));
        }
        pieDataSet.setColors(ColorTemplate.JOYFUL_COLORS);

        pieData = new PieData(pieDataSet);
        pieData.setValueFormatter(new CustomPercentFormatter());

        pieChart.setUsePercentValues(true);
        pieChart.setDescription(null);
        pieChart.getLegend().setEnabled(false);

        pieChart.setData(pieData);
        pieChart.invalidate();
    }

    void drawFavouriteIcons() {
        for (final CategoryExpense categoryExpense : adapter.getCachedFavCategories().values()) {
            final ImageView icon = new ImageView(getActivity());
            icon.setImageResource(categoryExpense.getIconId());

            icon.setScaleType(ImageButton.ScaleType.FIT_CENTER);
            CircleLayout.LayoutParams params = new CircleLayout.LayoutParams(120, 120);
            icon.setLayoutParams(params);

            icon.setPadding(30, 30, 30, 30);
            icon.setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.colorTransparent));

            circleLayout.addView(icon);
            circleLayout.setRotating(false);

            icon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TransactionFragment fragment = new TransactionFragment();
                    Bundle arguments = new Bundle();
                    arguments.putSerializable(getString(R.string.EXTRA_SECTION), categoryExpense);
                    fragment.setArguments(arguments);
                    getFragmentManager().beginTransaction()
                            .replace(R.id.master_layout, fragment, getString(R.string.transaction_fragment_tag))
                            .addToBackStack(getString(R.string.transaction_fragment_tag))
                            .commit();

                    pieChart.setCenterText(categoryExpense.getName() + "\n" + categoryExpense.getSum());
                    pieChart.setHoleColor(ContextCompat.getColor(getActivity(), R.color.colorGrey));
                    icon.setBackground(ContextCompat.getDrawable(getActivity(), R.drawable.icon_background));
                }
            });
        }
    }

    class CustomPercentFormatter implements IValueFormatter {

        private DecimalFormat mFormat;

        public CustomPercentFormatter() {
            mFormat = new DecimalFormat("###,###,##0.0");
        }

        @Override
        public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {

            if(value <= 0) return "";
            return mFormat.format(value) + " %";
        }
    }
}


