package rs.luka.belgradeunderground.data;

import rs.luka.belgradeunderground.Config;
import rs.luka.belgradeunderground.model.Line;
import rs.luka.belgradeunderground.model.Station;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by luka on 14.9.15.
 */
public class Finder {

    public static final int COST_TO_MIN_RATIO = 5500;
    public static final int SEARCH_WIDTH = 3;
    public static final int PATH_CANDIDATES = 50;
    public static double COEFFICIENT_USED_TO_DETERMINE_IF_PATH_IS_TOO_LONG = Line.COST_WALK; //todo fml, spava mi se
    private Station src;
    private List<PathTree> queue = new LinkedList<>();

    //private PathList current = new PathList();
    //private List<PathList> dropped = new LinkedList<>();

    //private int lastCheck = 0;
    //private boolean goodLength;
    private int iterator = 0;

    public Finder(Station src) {
        this.src = src;
    }

    public PathTree.Result find() { //todo fix
        /*if(isDirect()) {
            System.out.println("Direct");
            return stopList;
        }*/
        boolean done = false;
        PathTree tree = PathTree.init(src);
        for(int i=0; i<SEARCH_WIDTH; i++) {
            queue.add(tree.add(tree.getCurrentStation().getNextBestGuess(tree.getCurrentLine()), tree.getCurrentLine()));
        }
        while (!done) {
            for(int i=0; i<SEARCH_WIDTH; i++) {
                PathTree current = queue.get(iterator);
                if(current.isComplete()) {
                    tree=current;
                    done = true;
                    break;
                }
                queue.add(current.add(current.getCurrentStation().getNextBestGuess(current.getCurrentLine()), current.getCurrentLine()));
                iterator++;
            }
        }
        List<PathTree> more;
        more = additionalResults();
        more.add(tree);
        PathTree best = tree;
        for(PathTree pt : more) {
            if(pt.getCost() < best.getCost())
                best = pt;
        }
        return best.getPath();
    }

    public PathTree.Result dumbFind() {
        PathTree tree = PathTree.init(src);
        while(!tree.isComplete()) {
            tree = tree.add(tree.getCurrentStation().getNextBestGuess(tree.getCurrentLine()), tree.getCurrentLine());
        }
        return tree.getPath();
    }

    public PathTree.Result preciseFind() {
        List<PathTree.Result> possiblePaths = new ArrayList<>();
        PathTree nextNode = PathTree.init(src);
        for(int i=0; i<PATH_CANDIDATES; i++) {
            PathTree nextResult = findPath(nextNode);
            if(nextResult == null) break;
            possiblePaths.add(nextResult.getPath());
            nextNode = nextNode.rollbackToChild();
        }
        PathTree.Result best = null;
        double minCost = Double.MAX_VALUE;
        if(Config.DEBUG) {
            Collections.sort(possiblePaths, (PathTree.Result p1, PathTree.Result p2) -> {
                if(p1.getCost() < p2.getCost())
                    return -1;
                else if(p1.getCost() > p2.getCost())
                    return 1;
                return 0;
            });
            best = possiblePaths.get(0);
        } else {
            for (PathTree.Result possiblePath : possiblePaths) {
                if (possiblePath.getCost() < minCost) {
                    minCost = possiblePath.getCost();
                    best = possiblePath;
                }
            }
        }
        return best;
    }

    private PathTree findPath(PathTree tree) {
        while(!tree.isComplete()) {
            tree = tree.putNext();
            if(tree == null)
                return null;
        }
        return tree;
    }

    public List<PathTree> additionalResults() {
        List<PathTree> more = new LinkedList<>();
        while(iterator < queue.size()) {
            PathTree current = queue.get(iterator);
            if(current.isComplete()) {
                more.add(current);
            } else {
                queue.add(current.add(current.getCurrentStation().getNextBestGuess(current.getCurrentLine()), current.getCurrentLine()));
            }
            iterator++;
        }
        return more;
    }

    /*private boolean isDirect() {
        Map<String, Double> costs = new HashMap<>();
        double cost, minCost = Double.MAX_VALUE;
        Line optimal = null;
        Station srcStation = Base.getInstance().getStation(src), destStation = Base.getInstance().getStation(dest);
        List<Line> srcLines = srcStation.getLines();
        List<Line> destLines = destStation.getLines();
        destLines.stream().filter(srcLines::contains).forEach(l -> {
            stopList.add(new Stop(l, srcStation));
            stopList.add(new Stop(l, destStation));
        });
        for(int i=0; i<stopList.size(); i+=2) {
            cost = 0;
            Stop stop = stopList.get(i);
            stop.line.hopOn(Line.SMER_AUTO, stop.station);
            while(!stop.line.peekNext().equals(stopList.get(i+1).station)) {
                double nextCost = stop.line.goToNext();
                if(nextCost == -1) break;
                cost += nextCost;
            }
            costs.put(stop.line.getId(), cost);
            if(cost < minCost) {
                minCost = cost;
                optimal = stop.line;
            }
        }
        System.out.println("Costs: " + costs);
        System.out.println("Mincost: " + minCost);
        if(optimal != null) {
            Iterator<Stop> it = stopList.iterator();
            while(it.hasNext())
                if(!it.next().line.equals(optimal))
                    it.remove();
        }
        return !stopList.isEmpty();
    }*/

}
