package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSubQueryRemove(t *testing.T) {
	rule := loadRule(t, "SubQueryRemove")
	e := newEnv(t)
	r, rc := e.scan("u", 1)
	s, _ := e.scan("t", 1)
	before := e.f.ConstructSelect(r, e.filters(e.exists(s)))
	probe := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructTrue(), e.f.Metadata().AddColumn("probe", types.Bool)),
	}, opt.ColSet{})
	distinct := e.f.ConstructGroupBy(probe, nil, &memo.GroupingPrivate{GroupingCols: probe.Relational().OutputCols})
	joined := e.f.ConstructInnerJoin(r, distinct, nil, &memo.JoinPrivate{})
	want := e.f.ConstructProject(joined, nil, opt.MakeColSet(rc[0]))
	e.expectRewriteUpToFreshColumns(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(r, e.filters(e.f.ConstructNot(e.exists(s)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(r, e.filters(e.f.ConstructGt(e.v(rc[0]), e.num(0)))))
}
