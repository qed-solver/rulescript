package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateProjectMerge(t *testing.T) {
	rule := loadRule(t, "AggregateProjectMerge")
	e := newEnv(t)
	a, ac := e.scan("u", 1)
	b, bc := e.scan("t", 1)
	base := e.f.ConstructInnerJoin(a, b, nil, &memo.JoinPrivate{})
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	passthrough := opt.MakeColSet(bc[0], ac[0])
	project := e.f.ConstructProject(base, memo.ProjectionsExpr{}, passthrough)
	before := e.f.ConstructGroupBy(project,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(bc[0])), fcol)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(bc[0])})
	want := e.f.ConstructGroupBy(base,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(bc[0])), fcol)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(bc[0])})
	e.expectRewrite(rule, before, want)

	leftBase := e.f.ConstructLeftJoin(a, b, nil, &memo.JoinPrivate{})
	leftProject := e.f.ConstructProject(leftBase, memo.ProjectionsExpr{}, passthrough)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(leftProject,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(bc[0])), fcol)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(bc[0])}))

	partialProject := e.f.ConstructProject(base, memo.ProjectionsExpr{}, opt.MakeColSet(bc[0]))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(partialProject,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(bc[0])), fcol)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(bc[0])}))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(project,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(ac[0])), fcol)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(bc[0])}))
}
