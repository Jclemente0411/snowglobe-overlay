package com.snowglobe.overlay;

import java.util.*;

/**
 * Local, bounded US-English suggestions and conservative corrections.
 * No cloud, no keystroke history, no contact or personal dictionary lookups.
 * An intentionally small on-device vocabulary: not Gboard's language model.
 */
public final class KeyboardDictionary {
    private static final String FREQUENT =
        "the be to of and a in that have i it for not on with he as you do at this but his by from they we " +
        "say her she or an will my one all would there their what so up out if about who get which go me when " +
        "make can like time no just him know take people into year your good some could them see other than then " +
        "now look only come its over think also back after use two how our work first well way even new want because " +
        "any these give day most us is are was were been being am im ive dont cant wont didnt isnt wasnt arent " +
        "youre hes shes theyre thats its ive its i'll you'll we're let's should would could must may might need " +
        "hello hi hey yes yeah ok okay nope thanks thank please sorry sure fine great nice amazing awesome " +
        "love loved loving happy happier happiness friend friends family home house room kitchen bedroom bathroom " +
        "beautiful beautifulness best better big bigger biggest little small smaller large long short right left " +
        "today tomorrow yesterday morning afternoon evening night weekend week month spring summer autumn fall winter " +
        "monday tuesday wednesday thursday friday saturday sunday january february march april june july august " +
        "september october november december christmas holiday holidays merry santa snow snowflake snowman snowmen " +
        "lights light lighting christmas tree trees gifts present presents candy cane canes wreath fireplace " +
        "new year years time times later early last next always never often sometimes every usually really " +
        "something anything everything nothing someone anyone everyone nobody someone where why which whose " +
        "how much many few more less very too enough almost already still again yet soon before after during " +
        "while since until between around across through along down above below under inside outside beside " +
        "behind beyond near far away here there everywhere somewhere anywhere together apart alone with without " +
        "me him her us them my mine yours hers ours theirs his its myself yourself ourselves themselves " +
        "and or but if then else though although however therefore otherwise while when where unless because " +
        "both each either neither another same different various certain true false correct wrong accurate " +
        "good bad poor excellent perfect normal normalizing simple easy easier hard harder difficult " +
        "fun funny funnier kind nice mean rude friendly gentle strong fast faster slow slower quick quicker " +
        "beautiful pretty bright brighter dark darker sharp clear clearer smooth smoother fluid lag laggy " +
        "keyboard typing type typed text texting message messages send sent sending receive received reply " +
        "question answer answers search searching google pixel android samsung gboard goodlock phone tablet " +
        "app apps application device computer laptop mac windows keyboard screen display screen tap taps " +
        "click clicks clicked button buttons swipe swiping touch touches touching hold holding press pressed " +
        "space spacing alignment aligned centered center middle top bottom side sides position positions " +
        "vertical horizontal layout layouts design designs designer features feature version update updates " +
        "build builds release released testing test tests tested fix fixes fixed bugs issue issues problem " +
        "problems stable stability performance smooth fluidity animation animated effect effects glow glowing " +
        "color colors colourful colorful rainbow rgb red green blue yellow purple pink gold golden silver " +
        "white black orange gray grey aqua cyan midnight neon pastel bright dark light " +
        "auto autocorrect correction correct corrections suggestions suggestion predict prediction dictionary " +
        "privacy private safe safety secure security sensitive password passwords login checkout payment payments " +
        "purchase purchased purchasing store shop shopping buy bought buying price prices cost costs " +
        "money budget account accounts details settings control controls slider sliders toggle toggles " +
        "free premium quality professional polished custom customize customized customization " +
        "first second third fourth fifth six seven eight nine ten one two three four five " +
        "zero hundred thousand million plus minus more less add added adding remove removed deleting " +
        "choose chose choice selected select selecting switched switch change changed changes changing " +
        "open opened opening close closed closing start started starting stop stopped stopping pause paused " +
        "install installed installation uninstall downloading download upload uploaded uploading " +
        "ready complete completed finish finished finishing check checked checking verify verified " +
        "verify work working works worked does doing did done why what when where who how which " +
        "this that these those this is was it its at as in into onto on off to too from for " +
        "by and with about near over under around below above far away within between after before " +
        "car cars truck trucks jeep wrangler rubicon suv trailer ice cream business event booking " +
        "book books bookshop reading writing write writes wrote written story stories news article " +
        "video videos picture pictures image images photo photos screenshot screenshots camera " +
        "music song songs play playing player movie movies show shows game games gaming " +
        "food foods dinner lunch breakfast snack snacks coffee tea water drink drinks drinking " +
        "milk chocolate vanilla strawberry cookies cookie cake cakes sugar flour bread cheese " +
        "apple apples banana bananas orange oranges grapes grape melon lemon lemons peaches peach " +
        "pizza pasta chicken beef pork fish soup salad rice beans vegetable vegetables potatoes potato " +
        "dog dogs cat cats bird birds animal animals fish horse horses pet pets " +
        "person persons man woman men women child children kid kids baby babies boy boys girl girls " +
        "father mother mom dad sister brother brothers sisters uncle aunt cousin cousins grandparents " +
        "grandmother grandfather wife husband partner boyfriend girlfriend friends friendship " +
        "health healthy exercise workout gym running walking walk walks ran run running jogging " +
        "sleep sleeping slept wake awake waking dream dreams dreaming work worked working office " +
        "school class classes teacher teachers student students college university lesson lessons " +
        "learn learned learning teach taught study studying homework project projects " +
        "job jobs career careers meeting meetings call called calling phone phones email emails " +
        "address addresses number numbers name names date dates schedule scheduled planning plans " +
        "business company companies customer customers client clients team teams teammate teammates " +
        "management manager managers developer development develop developed developing product products " +
        "project projects deadline deadlines priority priorities task tasks list lists note notes " +
        "document documents file files folder folders github branch branches commit commits " +
        "pull push pushed publish published publishing send sent share shared sharing link links " +
        "website websites site sites internet online offline network connection connect connected " +
        "battery batteries charge charged charging power powered energy screen refresh frame frames " +
        "speed speeds latency delay delays fast faster quickly slow slower keyboard touchscreen " +
        "android iphone samsung galaxy apple google microsoft netflix disney youtube reddit instagram " +
        "facebook twitter tiktok whatsapp snapchat gmail maps calendar " +
        "happy birthday congratulations celebrate celebration party parties gift gifts " +
        "holiday holidays christmas xmas winter snowfall falling snowflakes lightbulb lights " +
        "amazing awesome wonderful gorgeous stunning fabulous incredible absolutely definitely " +
        "please kindly thanks welcome appreciate appreciated much really very excited excitedly " +
        "want wanted wanting need needed needs hope hoping wish wishing feel feels felt feeling " +
        "look looked looking looks seem seems seemed sounds sound hear heard hearing speak spoke speaking " +
        "listen listened listening watch watched watching read reads reading see saw seen seeing " +
        "think thought thinking believe believed believing know knew known knowing understand understood " +
        "remember remembered forget forgot forgotten learn learned plan planned planning build built " +
        "create created creating design designed designing fix fixing fixed adjust adjusted adjusting " +
        "improve improved improving enhance enhanced enhancing optimize optimized optimizing " +
        "finish finished report reported reporting check checking inspect inspected inspecting " +
        "review reviewed reviewing run running ran type typed typing test tested testing " +
        "take took taken give gave given find found finding get got gotten put putted " +
        "make made making buy bought purchase purchased spend spent sell sold selling " +
        "pay paid paying cost costing save saved saving earn earned earning grow grew grown " +
        "move moved moving stay stayed staying leave left leaving arrive arrived arriving " +
        "go went gone going come came coming walk walked walking drive drove driven driving " +
        "ride rode riding fly flew flown flying travel traveled traveling " +
        "open close start stop pause play hide show reveal select tap swipe press drag drop " +
        "copy paste undo redo replace insert cut delete space enter return backspace shift caps " +
        "left right middle center above below higher lower taller shorter wider narrower " +
        "equal equals same different smooth smoothness lag free no yes maybe probably perhaps " +
        "today tomorrow yesterday tonight tomorrow morning tomorrow afternoon " +
        "important interesting helpful useful effective efficient stable reliable " +
        "testing testable available unavailable possible impossible actually exactly accurately " +
        "basic advanced automatic automatically manual manually option optional " +
        "default defaulted current currently existing previous previously original new newer newest " +
        "latest recent recently next nexttime upcoming future past present " +
        "correct correctly wrong wrongly error errors warning warnings message messages " +
        "success successful successfully fail failed failure failing risk risks problem problems " +
        "wherever whenever whoever whichever whatever somehow meanwhile otherwise " +
        "asap immediately instantly instant real really quick quickly promptly " +
        "temperature weather rain snowy cloudy sunny sunshine storm windy wind winds " +
        "location locations place places area areas city cities state states country countries " +
        "united america american england english spanish french german language languages " +
        "morning noon midnight afternoon evening sunday monday friday saturday " +
        "getting got gotten having have has had doing did done being been is are was were " +
        "can cannot could should would will wont shall may might must " +
        "it's that's what's there's here's who's how's don't can't won't isn't aren't wasn't weren't " +
        "you'll you're we've i'm i'll i've he's she's they're we're hasn't haven't hadn't " +
        "doesn't didn't wouldn't shouldn't couldn't let's there's they're who's we're " +
        "hello goodbye bye later see yah yeah alright okay ok yes no yep nope maybe sure please";
    private static final Map<String,Integer> ranks=new HashMap<>();
    private static final ArrayList<String> ordered=new ArrayList<>();
    private static final Map<String,String> knownTypos=new HashMap<>();
    static{
        int rank=1;
        for(String word:FREQUENT.split(" ")){
            String w=word.toLowerCase(Locale.ROOT).trim();
            if(w.length()>0&&!ranks.containsKey(w)){ranks.put(w,rank++);ordered.add(w);}
        }
        String typos="teh=the adn=and taht=that thier=their recieve=receive recive=receive "+
            "definately=definitely becuase=because becasue=because seperate=separate "+
            "freind=friend frend=friend doesnt=doesn't dont=don't cant=can't wont=won't "+
            "im=I'm isnt=isn't didnt=didn't couldnt=couldn't wouldnt=wouldn't "+
            "shoud=should yuo=you yuor=your acheive=achieve adress=address "+
            "lagy=laggy laggey=laggy kkeyboard=keyboard keybord=keyboard "+
            "christams=christmas chrismas=christmas xmass=xmas "+
            "wierd=weird acommmodate=accommodate goverment=government "+
            "thnaks=thanks thakns=thanks plese=please pleas=please "+
            "becouse=because beutiful=beautiful beautifull=beautiful "+
            "helo=hello helooo=hello hellp=help smoth=smooth "+
            "amzaing=amazing amazeing=amazing increadible=incredible "+
            "yu=you wtih=with wih=with hte=the tje=the "+
            "toh=the tahts=that's tehre=there woudl=would "+
            "shoudl=should coudl=could donts=don't enought=enough "+
            "becaue=because wehre=where whne=when loook=look "+
            "calender=calendar aligment=alignment alignd=aligned "+
            "collor=color colours=colors colur=color "+
            "tpying=typing typeing=typing tryping=typing "+
            "lanugage=language tets=test repport=report "+
            "anaylsis=analysis reallly=really throgh=through "+
            "diffrent=different thier=their speach=speech "+
            "hwo=how teh=the its=it's";
        for(String pair:typos.split(" ")){
            int at=pair.indexOf('=');if(at>0)knownTypos.put(pair.substring(0,at),pair.substring(at+1));
        }
    }
    private KeyboardDictionary(){}

    public static boolean isKnown(String word){
        if(word==null)return false;
        return ranks.containsKey(word.toLowerCase(Locale.ROOT));
    }
    /** Only strong corrections: common explicit typos or a one-edit frequent candidate. */
    public static String correct(String raw){
        if(raw==null||raw.length()<2||raw.length()>22)return raw;
        String lower=raw.toLowerCase(Locale.ROOT);
        // Explicit high-confidence typos outrank raw corpus words such as "dont".
        // Do not auto-rewrite ambiguous correct English words such as "its".
        String direct=knownTypos.get(lower);
        if(direct!=null&&!"its".equals(lower)&&!"colours".equals(lower))
            return matchCase(raw,direct);
        if(isKnown(lower))return raw;
        if(raw.length()<3)return raw;
        String candidate=null;int best=Integer.MAX_VALUE;
        for(Map.Entry<String,Integer> entry:ranks.entrySet()){
            String word=entry.getKey();
            if(word.indexOf((char)39)>=0||Math.abs(word.length()-lower.length())>1)continue;
            int distance=distanceOne(lower,word);
            if(distance!=1)continue;
            int score=entry.getValue();
            // Restrict generic automatic replacements to high-frequency words.
            if(score>200)continue;
            if(score<best){candidate=word;best=score;}
        }
        return candidate==null?raw:matchCase(raw,candidate);
    }
    private static String matchCase(String original,String corrected){
        if(original.equals(original.toUpperCase(Locale.ROOT)))return corrected.toUpperCase(Locale.ROOT);
        if(Character.isUpperCase(original.charAt(0)))
            return Character.toUpperCase(corrected.charAt(0))+corrected.substring(1);
        return corrected;
    }
    /** Returns 0 equal, 1 one adjacent swap/insertion/deletion/replacement, 2 otherwise. */
    static int distanceOne(String a,String b){
        if(a.equals(b))return 0;
        int m=a.length(),n=b.length();
        if(Math.abs(m-n)>1)return 2;
        int edits=0,i=0,j=0;
        while(i<m&&j<n){
            if(a.charAt(i)==b.charAt(j)){i++;j++;continue;}
            edits++;if(edits>1)return 2;
            if(m>n)i++;
            else if(n>m)j++;
            else {if(i+1<m&&j+1<n&&a.charAt(i)==b.charAt(j+1)&&a.charAt(i+1)==b.charAt(j)){i+=2;j+=2;}
                  else{i++;j++;}}
        }
        return edits+(m-i)+(n-j)<=1?1:2;
    }
    public static List<String> suggest(String word){
        if(word==null||word.isEmpty()||word.length()>22)return Collections.emptyList();
        String lower=word.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> found=new LinkedHashSet<>(3);
        found.add(word);
        // Crucial responsiveness rule: do NOT run fuzzy edit-distance correction for
        // every keystroke. Only explicit well-known typos are offered live.
        String direct=knownTypos.get(lower);
        if(direct!=null)found.add(matchCase(word,direct));
        // The ranked vocabulary is constructed exactly once on class initialization.
        // Iterate in frequency order and stop once the three visible slots are filled.
        for(String candidate:ordered){
            if(candidate.startsWith(lower)&&!candidate.equals(lower)){
                found.add(matchCase(word,candidate));
                if(found.size()>=3)break;
            }
        }
        return new ArrayList<>(found);
    }
}
