package com.oto.edyd;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.oto.edyd.model.OilAmountDistribute;
import com.oto.edyd.model.OilDataBean;
import com.oto.edyd.utils.Common;
import com.oto.edyd.utils.Constant;
import com.oto.edyd.utils.OkHttpClientManager;
import com.squareup.okhttp.Request;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 金额分配模块
 * Created by lbz
 */
public class OilCardAmountDistributeActivity extends Activity implements View.OnClickListener {

    private LinearLayout back; //返回
    private EditText inputCarNumber; //车牌号
    private TextView amount; //总金额
    private double amountString;
    //    private TextView averageDistribute; //等额预分配
    private ListView listDistributeUser; //分配用户列表
    private TextView distributeCardNumber; //本次预分配卡数
    private TextView predictionDistributeAmount; //本次预分配金额
    private TextView submit; //提交
    private String submitUrl;
    private Common common;
    private List<OilDataBean> sendDataList;
    private OilCardAmountDistributeAdapter adapter;
    private String sessionUuid;
    private String enterpriseId;
    private Context mActivity;
    private String getMoneyUrl;//得到总金额url
    private SwipeRefreshLayout swipe_container;//下拉刷新
    private String OrgCode;

    private List<OilAmountDistribute> oilAmountDistributeList = new ArrayList<OilAmountDistribute>(); //列表数据

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.oil_card_amount_distribute);
        mActivity = this;
        initFields();
        //设置item点击监听。
        listDistributeUser.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                EditText editText = (EditText) view.findViewById(R.id.prediction_distribute_amount);
                editText.requestFocus();
                editText.setFocusable(true);
                // editText.setFocusableInTouchMode(true);
            }

            public void noNothingSelected(AdapterView<?> parent) {
                listDistributeUser.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            }
        });
        //列表设置滚动点击监听。
        listDistributeUser.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (SCROLL_STATE_TOUCH_SCROLL == scrollState) {
                    View currentFocus = getCurrentFocus();
                    if (currentFocus != null) {
                        currentFocus.clearFocus();
                    }
                }
            }

            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            }
        });

    }


    private void initFields() {
        swipe_container = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        swipe_container.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            /**
             * 刷新要做的操作
             */
            @Override
            public void onRefresh() {
                getAllMoney(getMoneyUrl);
                seachCar("");
            }
        });

        back = (LinearLayout) findViewById(R.id.back);
        inputCarNumber = (EditText) findViewById(R.id.type_car_number);

        inputCarNumber.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                seachCar(s.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
        amount = (TextView) findViewById(R.id.amount);
        listDistributeUser = (ListView) findViewById(R.id.list_distribute_user);
        //获得总金额数据
        common = new Common(getSharedPreferences(Constant.LOGIN_PREFERENCES_FILE, Context.MODE_PRIVATE));
        sessionUuid = common.getStringByKey(Constant.SESSION_UUID);
        enterpriseId = common.getStringByKey(Constant.ENTERPRISE_ID);
        OrgCode = common.getStringByKey(Constant.ORG_CODE);
        //iqueryOilShengByEnterpriseInfo.json?sessionUuid=&enterpriseId=54&OrgCode=1&sysFullName=bgwl
        getMoneyUrl = Constant.ENTRANCE_PREFIX + "iqueryOilShengByEnterpriseInfo.json?sessionUuid="
                + sessionUuid + "&enterpriseId=" + enterpriseId + "&OrgCode=" + OrgCode;

        getAllMoney(getMoneyUrl);
        //获取车牌信息
        seachCar("");

        //提交url
        submitUrl = Constant.ENTRANCE_PREFIX + "inquerePredistribution.json";


        distributeCardNumber = (TextView) findViewById(R.id.current_distribute_card_number);
        predictionDistributeAmount = (TextView) findViewById(R.id.current_prediction_distribute_amount);
        //提交
        submit = (TextView) findViewById(R.id.submit);
        submit.setOnClickListener(this);
        back.setOnClickListener(this);
    }


    private void getAllMoney(String url) {
        OkHttpClientManager.getAsyn(url, new OkHttpClientManager.ResultCallback<String>() {
            @Override
            public void onError(Request request, Exception e) {
                Toast.makeText(getApplicationContext(), "获取预分配金额异常", Toast.LENGTH_SHORT).show();
            }

            //            {"status":"200","flag":"SUCCESS","message":"操作成功",
//                    "action":"iqueryOilShengByEnterpriseInfo","version":"v1.0","format":"json","rows":[2000]}
            @Override
            public void onResponse(String response) {
                Common.printErrLog("金额数据" + response);
                JSONObject jsonObject;
                JSONArray jsonArray;
                try {
                    jsonObject = new JSONObject(response);
                    if (!jsonObject.getString("status").equals(Constant.LOGIN_SUCCESS_STATUS)) {
                        Toast.makeText(getApplicationContext(), getString(R.string.pull_orgcode_exception), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    jsonArray = jsonObject.getJSONArray("rows");
                    double mon = jsonArray.getDouble(0);
                    if (mon == 0.0 && mon == 0) {
                        Common.showToast(mActivity, "暂无数据");
                    }
                    amountString = mon;
                    amount.setText(mon + "");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void searchCarName(View view) {
        String carNumber = inputCarNumber.getText().toString().trim();
        //查找
        // inqueryOilBalanceExprot.json?sessionUuid=6a7ca347b9914bcb909b54a88b4d555d&enterpriseId=0&carId=%E9%97%BDD39698
        seachCar(carNumber);
    }

    private void seachCar(String carNumber) {
        String url = Constant.ENTRANCE_PREFIX + "inqueryOilBalanceExprot.json?sessionUuid="
                + sessionUuid + "&enterpriseId=" + enterpriseId + "&carId=" + carNumber + "&OrgCode=" + OrgCode;
        OkHttpClientManager.getAsyn(url, new OkHttpClientManager.ResultCallback<String>() {
            @Override
            public void onError(Request request, Exception e) {
                Toast.makeText(getApplicationContext(), "获取信息失败", Toast.LENGTH_SHORT).show();
                swipe_container.setRefreshing(false);
            }

            @Override
            public void onResponse(String response) {

                Common.printErrLog("获取查询信息" + response);
                JSONObject jsonObject;
                JSONArray jsonArray;
                try {
                    jsonObject = new JSONObject(response);
                    if (!jsonObject.getString("status").equals(Constant.LOGIN_SUCCESS_STATUS)) {
                        Toast.makeText(getApplicationContext(), "获取查询信息失败", Toast.LENGTH_SHORT).show();
                        swipe_container.setRefreshing(false);
                        return;
                    }
                    jsonArray = jsonObject.getJSONArray("rows");
                    requestDistributeUserList(jsonArray);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    public void clearAll(View view) {
        clearALLAmount();
    }

    private void clearALLAmount() {
        for (OilAmountDistribute oad : oilAmountDistributeList) {
            oad.setAmount(null);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onClick(View v) {
        Intent intent;
        switch (v.getId()) {
            case R.id.back: //返回
                finish();
                break;
            case R.id.submit: //提交
                if (!isAmount()) {
                    Toast.makeText(mActivity, "您未填写分配金额", Toast.LENGTH_SHORT).show();
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setTitle("提交");
                builder.setMessage("你确定要提交吗？");
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        submitResult();
                    }
                });
                builder.setNegativeButton("取消", null);
                builder.show();
                break;
        }
    }

    private void submitResult() {

        Map<String, String> params = new HashMap<String, String>();
        params.put("sessionUuid", sessionUuid);
        sendDataList = new ArrayList<OilDataBean>();
        fillSendData(sendDataList);
        String sendData = common.createJsonString(sendDataList);
        params.put("sendData", sendData);
        OkHttpClientManager.postAsyn(submitUrl, params, new OkHttpClientManager.ResultCallback<String>() {
            @Override
            public void onError(Request request, Exception e) {
                Toast.makeText(mActivity, "提交失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onResponse(String response) {
                JSONObject jsonObject;
                try {
                    jsonObject = new JSONObject(response);
                    if (!jsonObject.getString("status").equals(Constant.LOGIN_SUCCESS_STATUS)) {
                        Toast.makeText(getApplicationContext(), "提交失败", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Toast.makeText(getApplicationContext(), "提交成功", Toast.LENGTH_SHORT).show();
                    clearALLAmount();
                    getAllMoney(getMoneyUrl);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 是否分配？
     *
     * @return
     */
    private boolean isAmount() {
        boolean is = false;
        for (OilAmountDistribute oad : oilAmountDistributeList) {
            if (!TextUtils.isEmpty(oad.getAmount())) {
                is = true;
            }
        }
        return is;
    }

    private void fillSendData(List<OilDataBean> list) {

//        private List<OilAmountDistribute> oilAmountDistributeList
        for (OilAmountDistribute oad : oilAmountDistributeList) {
            if (!TextUtils.isEmpty(oad.getAmount())) {
                OilDataBean bean = new OilDataBean();
                bean.setReleationId(oad.getReleationId());
                bean.setOilCardId(oad.getOilCardId());
                bean.setCardId(oad.getCardNumber());
                bean.setAllocatedamount(oad.getAmount());
                list.add(bean);
            }
        }
    }


    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0x12: //油卡金额数据返回执行
                    swipe_container.setRefreshing(false);
                    if (adapter == null) {
                        adapter = new OilCardAmountDistributeAdapter(OilCardAmountDistributeActivity.this);
                        listDistributeUser.setAdapter(adapter);
                    } else {
                        adapter.notifyDataSetChanged();
                    }
                    break;
            }
        }
    };

    /**
     * 请求预分配用户列表
     */
    private void requestDistributeUserList(JSONArray jsonArray) throws JSONException {

        oilAmountDistributeList.clear();
        //假数据
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject obj = jsonArray.getJSONObject(i);
            OilAmountDistribute oilAmountDistribute = new OilAmountDistribute();
            oilAmountDistribute.setCardNumber(obj.getString("cardId"));
            oilAmountDistribute.setCarNumber(obj.getString("cardHolder"));
            oilAmountDistribute.setOilCardId(obj.getString("oilId"));
            oilAmountDistribute.setReleationId(obj.getString("ID"));
            oilAmountDistributeList.add(oilAmountDistribute);
        }
        Message message = new Message();
        message.what = 0x12;
        handler.sendMessage(message);
    }

    /**
     * 设置操作数据
     */
    private void setOperationData() {

    }

    /**
     * 自定义适配器
     */
    private class OilCardAmountDistributeAdapter extends BaseAdapter {

        private Context context;
        private LayoutInflater inflater;

        private OilCardAmountDistributeAdapter(Context context) {
            this.context = context;
            inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return oilAmountDistributeList.size();
        }

        @Override
        public Object getItem(int position) {
            return oilAmountDistributeList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            //item数据
            final OilAmountDistribute oilAmountDistribute = oilAmountDistributeList.get(position);
            TextView tCarNumber; //车牌号
            TextView tCardNumber; //卡号
            final EditText tAmount; //金额
            View view = inflater.inflate(R.layout.distribute_user_item, null);
            tCarNumber = (TextView) view.findViewById(R.id.car_number);
            tCardNumber = (TextView) view.findViewById(R.id.card_id);
            //设置监听
            tAmount = (EditText) view.findViewById(R.id.prediction_distribute_amount);
            tAmount.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void onTextChanged(CharSequence s, int i, int i1, int i2) {
                    //设置小数只能是两位
                    if (s.toString().contains(".")) {
                        if (s.length() - 1 - s.toString().indexOf(".") > 2) {
                            s = s.toString().subSequence(0,
                                    s.toString().indexOf(".") + 3);
                            tAmount.setText(s);
                            tAmount.setSelection(s.length());
                        }
                    }
                    if (s.toString().trim().substring(0).equals(".")) {
                        s = "0" + s;
                        tAmount.setText(s);
                        tAmount.setSelection(2);
                    }
                    if (s.toString().startsWith("0")
                            && s.toString().trim().length() > 1) {
                        if (!s.toString().substring(1, 2).equals(".")) {
                            tAmount.setText(s.subSequence(0, 1));
                            tAmount.setSelection(1);
//                            return;
                        }
                    }

                    //设置预分配卡数
//                    String s1 = s.toString();
//                    if (!TextUtils.isEmpty(s1)) {
//                        char c = s1.charAt(0);
//                        if (c == '.') {
//                            oilAmountDistribute.setAmount("");
//                            adapter.notifyDataSetChanged();
//                            return;
//                        }
//                    }
                    oilAmountDistribute.setAmount(tAmount.getText().toString());
                    sumCardNum();
                    sumMoneyNum();
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });


            tCarNumber.setText(oilAmountDistribute.getCarNumber());
            tCardNumber.setText(oilAmountDistribute.getCardNumber());
            tAmount.setText(oilAmountDistribute.getAmount());
            return view;
        }
    }

    /**
     * 设置分配总金额
     */
    private boolean sumMoneyNum() {
        double mon = 0;
        for (OilAmountDistribute oad : oilAmountDistributeList) {
            String amount = oad.getAmount();
            if (!TextUtils.isEmpty(amount)) {

                mon = mon + Double.parseDouble(amount);
                if (mon > amountString) {
                    Toast.makeText(mActivity, "你的余额不够", Toast.LENGTH_SHORT).show();
                    oad.setAmount("");
                    adapter.notifyDataSetChanged();
                    return false;
                }
            }
        }
        //预分配金额

        predictionDistributeAmount.setText(getNormalDouble(mon) + "");
        //额度账余额
        double balanceNum = amountString - mon;
        double nd = getNormalDouble(balanceNum);
        amount.setText(nd + "");
        return true;
    }

    private double getNormalDouble(double balanceNum) {
        BigDecimal bdl = new BigDecimal(balanceNum);
        bdl = bdl.setScale(2, BigDecimal.ROUND_HALF_UP);
        return bdl.doubleValue();
    }


    private void sumCardNum() {
        int i = 0;
        for (OilAmountDistribute oad : oilAmountDistributeList) {
            if (!TextUtils.isEmpty(oad.getAmount())) {
                i++;
            }
        }
        distributeCardNumber.setText(i + "");
    }
}
