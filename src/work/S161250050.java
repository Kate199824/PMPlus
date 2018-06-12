package work;

import bottom.BottomMonitor;
import bottom.BottomService;
import bottom.Task;
import main.Schedule;

import java.io.IOException;

/**
 *
 * 注意：请将此类名改为 S+你的学号   eg: S161250001
 * 提交时只用提交此类和说明文档
 *
 * 在实现过程中不得声明新的存储空间（不得使用new关键字，反射和java集合类）
 * 所有新声明的类变量必须为final类型
 * 不得创建新的辅助类
 *
 * 可以生成局部变量
 * 可以实现新的私有函数
 *
 * 可用接口说明:
 *
 * 获得当前的时间片
 * int getTimeTick()
 *
 * 获得cpu数目
 * int getCpuNumber()
 *
 * 对自由内存的读操作  offset 为索引偏移量， 返回位置为offset中存储的byte值
 * byte readFreeMemory(int offset)
 *
 * 对自由内存的写操作  offset 为索引偏移量， 将x写入位置为offset的内存中
 * void writeFreeMemory(int offset, byte x)
 *
 */
public class S161250050 extends Schedule{

    //这个地址中存了PCB的数量
    private static final int ptr_PCB_NUM = 0;

    private static final int PCB_TABLE_BASE = 2;//pcb表的基地址

    private static final int CPU_STATE_TABLE_BASE = 15986;//cpu状态表的基地址

    private static final int RESOURSE_STATE_TABLE_BASE = 15994;//资源表基地址

    private static final int ptr_RESURCE_RECORD_LEN = 16122;//用到哪里了

    private static final int RESURCE_RECORD_BASE = 16124;//记录每个进程需要的资源

    @Override
    public void ProcessSchedule(Task[] arrivedTask, int[] cpuOperate) {
        /**
         * write your code here
         */
        //1.一个地址可以用2个字节搞定
        //2.资源号都减一可以用1个字节搞定
        //PCB的样子：共16字节
        //  0     1     2     3     4     5     6     7     8     9     10     11     12     13     14     15
        //  |  task_id  | end       |       left_time       |       wait_time        |resource_base | resource_num|

        //内存的样子
        //   0   pcb个数
        //   2
        //   |    pcb1
        //   16
        //  ......
        //   15970
        //    |    pcb 999
        //   15984
        //   15986  cpu1_state   记录这个cpu被哪个进程用
        //   15988     2
        //   15990     3
        //   15992     4
        //   15994   resource0、1
        //   ......
        //   16120   resource126、127
        //   16122    已记录资源个数
        //   16124    记录资源
        //
        System.out.println("");
        System.out.println("");
        System.out.println("");
        System.out.println("");
        System.out.println("<<<<<<<<<<<<<<<<SCHEDULING>>>>>>>>>>>>>>>>>>");
        int cpu_num = getCpuNumber();

        clean();
        free_all_resources();
        //1.record tasks
        if (arrivedTask != null) {
            for (Task task : arrivedTask) {
                record_task(task);
            }
        }

    //    print_all_task_id();

        //schedule
        int free_cpu = cpu_num;
        int task_id = 0;
        boolean task_run_flag = false;


        while(free_cpu>0 && (task_id = get_next_task())!=0 ){
            free_cpu--;
            System.out.println("[SELECT TASK ID IS: "+task_id+"]");
            System.out.println("[FREE CPU NUM IS: "+free_cpu+"]");
            //检测上一时刻这个task有没有run
            for(int i=0;i<cpu_num;i++){
                if(get_cpu_task_id(i) == task_id){
                    cpuOperate[i] = task_id;
                    task_run_flag = true;
                    break;
                }
            }

            //上一时刻没有被run，选一个cpu
            if(!task_run_flag){
                for(int i=0;i<cpu_num;i++){
                    if(get_cpu_task_id(i) == 0){
                        write_cpu_task_id(i,task_id);
                        cpuOperate[i] = task_id;
                        break;
                    }
                }
            }
        }


        //update cpu state table
        for(int i=0;i<cpuOperate.length;i++){
            write_cpu_task_id(i,cpuOperate[i]);
        }

        //update task wait time in PCB!
        //PCB的样子：共16字节
        //  0     1     2     3     4     5     6     7     8     9     10     11     12     13     14     15
        //  |  task_id  | end |     |       left_time       |       wait_time        |resource_base | resource_num|
        int total_task_num = readShort(ptr_PCB_NUM);
     //   System.out.println("-------------UPDATING PCB--------------------");
        for(int i=0;i<total_task_num;i++){
            int my_pcb_base =  PCB_TABLE_BASE + 16*i;
            int my_task_id = readShort(my_pcb_base);
        //    System.out.println("TASK "+i+" ID: "+my_task_id);
            if(!is_task_running(my_task_id, cpuOperate)){
                writeInteger(my_pcb_base+8, readInteger(my_pcb_base+8)+1);
            }
        }

        System.out.print("CPU STATE:  ");
        for(int i=0;i<cpuOperate.length;i++){
            System.out.print(i+" :"+" "+cpuOperate[i]+";  ");

        }
        System.out.println("");
        System.out.println(">>>>>>>>>>>>>>>>>>>GO TO RUN<<<<<<<<<<<<<<<<<<<<<<<");
    }


    private void clean(){
        //1.clean cpu table
        //置0
        int cpu_num = getCpuNumber();
        for(int i=0;i<cpu_num;i++){
            writeShort(CPU_STATE_TABLE_BASE+2*i,0);
        }

    }

    /**
     * 选择一个task运行
     * @return
     */
    private int get_next_task(){
        System.out.println("SELECTING NEXT TASK...");
        int selected_task_id = 0;
        int task_pcb_entrance = -1;//记录选中的task对应第几条pcb
        double highest_task_score = 0.0;
        int total_task_num = readShort(ptr_PCB_NUM);
//        System.out.println("TOTAL TASK NUM IN PCB: "+total_task_num);

        for(int i=0;i<total_task_num;i++){
            int my_pcb_base = PCB_TABLE_BASE + 16*i;
            int my_task_end = readByte(my_pcb_base+2);
            if(my_task_end == 0){//task没结束
                //判断是否有所有资源
                int my_resource_base = readShort(my_pcb_base+12);
                int my_resource_length = readShort(my_pcb_base+14);
                if(is_task_has_all_resource_needed(my_resource_base,my_resource_length)){//具备所有资源
                    int my_wait_time = readInteger(my_pcb_base+8);
                    int my_left_time = readInteger(my_pcb_base+4);
                    double my_score = get_task_score(my_wait_time,my_left_time);
             //       System.out.println("----TASK "+i+" GOT SCORE "+my_score);
                    if(my_score > highest_task_score){
                        //  替换
                        highest_task_score = my_score;
                        task_pcb_entrance = i;
                    }
                }
            }
        }

        //
        if(task_pcb_entrance != -1){
        //    System.out.println("SELECT TASK PCB ID: "+task_pcb_entrance);

            //mark resources
            int selected_pcb_base = PCB_TABLE_BASE + 16*task_pcb_entrance;

            selected_task_id = readShort(selected_pcb_base);

         //   System.out.println("SELECT TASK ID: " + selected_task_id);

            int selected_resource_base = readShort(selected_pcb_base+12);
            int selected_resource_length = readShort(selected_pcb_base+14);
            mark_resources(selected_resource_base,selected_resource_length);
            //lefttime--
            int new_left_time = readInteger(selected_pcb_base+4)-1;
            writeInteger(selected_pcb_base+4, new_left_time);
            //mark end
            if(new_left_time == 0){
                writeByte(selected_pcb_base+2,1);
            }
        }

        return selected_task_id;
    }

    private double get_task_score(int wait, int left){
//        double res = wait*10 + left/2;
//        return res;
        double score = 10.0 / ((double) left);
        score += wait < 5 ? 1.0 :
                wait < 15 ? (2.0 * wait + 1.0) : (double) (pow(2, wait - 11));
        return score;
    }

    /**
     * 判断一个task是否有了所有资源
     * @return
     */
    private boolean is_task_has_all_resource_needed(int resource_base, int resource_length){
        for(int i=0;i<resource_length;i++){
            int resource_id = readByte(resource_base + i);
            if(!is_resource_free(resource_id)){
                return false;
            }
        }
        return true;
    }

    /**
     * 将这个task需要的资源标记为已用
     * @param resource_base
     * @param resource_length
     */
    private void mark_resources(int resource_base, int resource_length){
        for(int i=0;i<resource_length;i++){
            int resource_id = readByte(resource_base + i);
            writeByte(RESOURSE_STATE_TABLE_BASE + resource_id*1,1);
        }
    }


    private void free_all_resources(){
        for(int i=0;i<128;i++){
            writeByte(RESOURSE_STATE_TABLE_BASE+i,0);
        }
    }

    /**
     * 给一个resourceid（0-127） 判断是否可用
     * @return
     */
    private boolean is_resource_free(int resource_id){
        int state = readByte(RESOURSE_STATE_TABLE_BASE + resource_id*1);
        if(state == 0){//0表示free
            return true;
        }
        else{
            return false;
        }
    }

    /**
     *task是否正在运行
     * @return
     */
    private boolean is_task_running(int task_id, int[] running_list){
        for(int i : running_list){
            if(i == task_id){
                return true;
            }
        }
        return false;
    }


    /**
     *
     */
    private void record_task(Task task){
        //pcb的基地址
//        System.out.println("RECORD TASK: "+task.toString());
        int pcb_num = readShort(ptr_PCB_NUM);
//        System.out.println("----PCB NUM: "+pcb_num);
        int my_pcb_location = 16 * pcb_num + PCB_TABLE_BASE;
//        System.out.println("----PCB BEGIN LOCATION: "+my_pcb_location);
        //PCB的样子：共16字节
        //  0     1     2     3     4     5     6     7     8     9     10     11     12     13     14     15
        //  |  task_id  | end       |       left_time       |       wait_time        |resource_base | resource_num|
        writeShort(my_pcb_location,task.tid);//id
//        System.out.println("----RECORD TASK ID: "+task.tid);
        writeByte(my_pcb_location+2, 0);//没结束
        writeInteger(my_pcb_location+4,task.cpuTime);//剩余时间
        writeInteger(my_pcb_location+8,0);//已等待时间

        int resource_record_num = readShort(ptr_RESURCE_RECORD_LEN);

        int my_resource_base = RESURCE_RECORD_BASE + resource_record_num;

        writeShort(my_pcb_location+12, my_resource_base);
        int my_resource_num = task.resource.length;
        writeShort(ptr_RESURCE_RECORD_LEN, resource_record_num+ my_resource_num);
        writeShort(my_pcb_location+14,my_resource_num);

        for(int i=0;i<my_resource_num;i++){
            writeByte(my_resource_base+i, task.resource[i]-1);//记录资源
        }

        pcb_num++;
        writeShort(ptr_PCB_NUM,pcb_num);

        int task_id_debug = readShort(my_pcb_location);
//        System.out.println("----READ RECORD TASK ID: "+task_id_debug);
       // print_all_task_id();
    }

    /**
     * 写cpu运行哪个task
     * @param cpu_num
     * @param task_id
     */
    private void write_cpu_task_id(int cpu_num,int task_id){
        writeShort(CPU_STATE_TABLE_BASE + 2*cpu_num, task_id);
    }

    /**
     * 获得cpu当前运行的taskid
     * @param cpu_num
     * @return
     */
    private int get_cpu_task_id(int cpu_num){
        int task_id = readShort(CPU_STATE_TABLE_BASE + 2*cpu_num);
        return task_id;
    }


    /**
     * 向自由内存中 读一个int型整数
     * @param beginIndex
     * @return
     */
    private int readInteger(int beginIndex){
        int ans = 0;
        ans += (readFreeMemory(beginIndex)&0xff)<<24;
        ans += (readFreeMemory(beginIndex+1)&0xff)<<16;
        ans += (readFreeMemory(beginIndex+2)&0xff)<<8;
        ans += (readFreeMemory(beginIndex+3)&0xff);
        return ans;
    }

    /**
     * 向自由内存中写一个int型整数
     * @param beginIndex
     * @param value
     */
    private void writeInteger(int beginIndex, int value){
        writeFreeMemory(beginIndex+3, (byte) ((value&0x000000ff)));
        writeFreeMemory(beginIndex+2, (byte) ((value&0x0000ff00)>>8));
        writeFreeMemory(beginIndex+1, (byte) ((value&0x00ff0000)>>16));
        writeFreeMemory(beginIndex, (byte) ((value&0xff000000)>>24));
    }

    /**
     * 向自由内存中写一个short型整数
     * @param beginIndex
     * @param value
     */
    private void writeShort(int beginIndex, int value){
        writeFreeMemory(beginIndex+1, (byte) ((value&0x000000ff)));
        writeFreeMemory(beginIndex, (byte) ((value&0x0000ff00)>>8));
    }

    /**
     * 向自由内存中 读一个short型整数
     * @param beginIndex
     * @return
     */
    private int readShort(int beginIndex){
        int ans = 0;
        ans += (readFreeMemory(beginIndex)&0xff)<<8;
        ans += (readFreeMemory(beginIndex+1)&0xff);
        return ans;
    }

    /**
     * 向自由内存中写一个byte型整数
     * @param beginIndex
     * @param value
     */
    private void writeByte(int beginIndex, int value){
        writeFreeMemory(beginIndex, (byte) ((value&0x000000ff)));
    }


    /**
     * 向自由内存中 读一个byte型整数
     * @param beginIndex
     * @return
     */
    private int readByte(int beginIndex){
        int ans = 0;
        ans += (readFreeMemory(beginIndex)&0xff);
        return ans;
    }

    private int pow(int x, int n) {
        int res=1;
        for (int i = 0; i < n; i++) {
            res *= x;
        }
        return res;
    }

    private void print_all_task_id(){
        System.out.println("..........PRINTING ALL TASK IDS.......");
        int pcb_num = readShort(ptr_PCB_NUM);
        for(int i=0;i<pcb_num;i++){
            int location = PCB_TABLE_BASE + 16*i;
            int id = readShort(location);
            System.out.println("TASK "+i+" ID:"+id);
        }
        System.out.println("....................................");
    }

    /**
     * 执行主函数 用于debug
     * 里面的内容可随意修改
     * 你可以在这里进行对自己的策略进行测试，如果不喜欢这种测试方式，可以直接删除main函数
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        // 定义cpu的数量
        int cpuNumber = 2;
        // 定义测试文件
        String filename = "src/testFile/rand_8.csv";

        BottomMonitor bottomMonitor = new BottomMonitor(filename,cpuNumber);
        BottomService bottomService = new BottomService(bottomMonitor);
        Schedule schedule =  new S161250050();
        schedule.setBottomService(bottomService);

        //外部调用实现类
        for(int i = 0 ; i < 1000 ; i++){
            Task[] tasks = bottomMonitor.getTaskArrived();
            int[] cpuOperate = new int[cpuNumber];

            // 结果返回给cpuOperate
            schedule.ProcessSchedule(tasks,cpuOperate);

            try {
                bottomService.runCpu(cpuOperate);
            } catch (Exception e) {
                System.out.println("Fail: "+e.getMessage());
                e.printStackTrace();
                return;
            }
            bottomMonitor.increment();
        }

        //打印统计结果
        bottomMonitor.printStatistics();
        System.out.println();

        //打印任务队列
        bottomMonitor.printTaskArrayLog();
        System.out.println();

        //打印cpu日志
        bottomMonitor.printCpuLog();


        if(!bottomMonitor.isAllTaskFinish()){
            System.out.println(" Fail: At least one task has not been completed! ");
        }
    }
}

