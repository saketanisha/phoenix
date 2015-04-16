package org.apache.phoenix.calcite.rel;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexNode;
import org.apache.phoenix.calcite.CalciteUtils;
import org.apache.phoenix.compile.QueryPlan;
import org.apache.phoenix.compile.RowProjector;
import org.apache.phoenix.compile.OrderByCompiler.OrderBy;
import org.apache.phoenix.execute.ClientScanPlan;

public class PhoenixLimit extends Sort implements PhoenixRel {
    public final Integer statelessFetch;

    public PhoenixLimit(RelOptCluster cluster, RelTraitSet traits, RelNode input, RelCollation collation, RexNode offset, RexNode fetch) {
        super(cluster, traits, input, collation, offset, fetch);
        Object value = fetch == null ? null : CalciteUtils.evaluateStatelessExpression(fetch);
        this.statelessFetch = value == null ? null : ((Number) value).intValue();        
        assert getConvention() == PhoenixRel.CONVENTION;
        assert getCollation().getFieldCollations().isEmpty();
    }

    @Override
    public PhoenixLimit copy(RelTraitSet traitSet, RelNode newInput,
            RelCollation newCollation, RexNode offset, RexNode fetch) {
        return new PhoenixLimit(getCluster(), traitSet, newInput, newCollation, offset, fetch);
    }

    @Override 
    public RelOptCost computeSelfCost(RelOptPlanner planner) {
        double rowCount = RelMetadataQuery.getRowCount(this);
        return planner.getCostFactory()
                .makeCost(rowCount, 0, 0)
                .multiplyBy(PHOENIX_FACTOR);
    }
    
    @Override 
    public double getRows() {
        double rows = super.getRows();        
        // TODO Should we apply a factor to ensure that a limit can be propagated to
        // lower nodes as much as possible?
        if (this.statelessFetch == null)
            return rows;

        return Math.min(this.statelessFetch, rows);
    }

    @Override
    public QueryPlan implement(Implementor implementor) {
        assert getConvention() == getInput().getConvention();
        
        QueryPlan plan = implementor.visitInput(0, (PhoenixRel) getInput());
        // TODO only wrap with ClientScanPlan 
        // if (plan.getLimit() != null);
        // otherwise add limit to "plan"
        return new ClientScanPlan(plan.getContext(), plan.getStatement(), 
                implementor.getTableRef(), RowProjector.EMPTY_PROJECTOR, 
                statelessFetch, null, OrderBy.EMPTY_ORDER_BY, plan);
    }
}