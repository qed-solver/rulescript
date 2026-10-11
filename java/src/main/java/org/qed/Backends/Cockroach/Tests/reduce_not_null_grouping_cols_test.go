package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestReduceNotNullGroupingCols(t *testing.T) {
	rule := loadRule(t, "ReduceNotNullGroupingCols")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	alias := e.f.Metadata().AddColumn("alias", types.Int)
	project := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), alias),
	}, opt.MakeColSet(cols[0]))
	before := e.f.ConstructGroupBy(project, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0], alias),
	})
	grouped := e.f.ConstructGroupBy(project, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	})
	want := e.f.ConstructProject(grouped, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(cols[0]), alias),
	}, opt.MakeColSet(cols[0]))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, grouped)
	wrongProject := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(1), alias),
	}, opt.MakeColSet(cols[0]))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(wrongProject, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0], alias),
	}))
}
