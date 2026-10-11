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
	p0 := e.f.Metadata().AddColumn("p0", types.Int)
	p1 := e.f.Metadata().AddColumn("p1", types.Int)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	projections := memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(bc[0]), p0),
		e.f.ConstructProjectionsItem(e.v(ac[0]), p1),
	}
	project := e.f.ConstructProject(base, projections, opt.ColSet{})
	before := e.f.ConstructGroupBy(project,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(p0)), fcol)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(p0)})
	want := e.f.ConstructGroupBy(base,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(bc[0])), fcol)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(bc[0])})
	e.expectRewrite(rule, before, want)

	leftBase := e.f.ConstructLeftJoin(a, b, nil, &memo.JoinPrivate{})
	leftProject := e.f.ConstructProject(leftBase, projections, opt.ColSet{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(leftProject,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(p0)), fcol)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(p0)}))

	identityProject := e.f.ConstructProject(base, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(ac[0]), p1),
		e.f.ConstructProjectionsItem(e.v(bc[0]), p0),
	}, opt.ColSet{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(identityProject,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(p0)), fcol)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(p0)}))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(project,
		memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(p1)), fcol)},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(p0)}))
}
