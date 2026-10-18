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
	r, rCols := e.scan("t", 1)
	s, _ := e.scan("u", 1)
	before := e.f.ConstructSelect(r, e.filters(e.exists(s)))

	s2 := s
	trueCol := e.f.Metadata().AddColumn("true", types.Bool)
	probe := e.f.ConstructProject(s2, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(memo.TrueSingleton, trueCol)}, opt.ColSet{})
	distinct := e.f.ConstructGroupBy(probe, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(trueCol)})
	joined := e.f.ConstructInnerJoin(r, distinct, memo.TrueFilter, memo.EmptyJoinPrivate)
	want := e.f.ConstructProject(joined, memo.EmptyProjectionsExpr, opt.MakeColSet(rCols[0]))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	other, _ := e.scan("u", 1)
	e.expectNoMatch(rule, e.f.ConstructSelect(r, e.filters(e.f.ConstructNot(e.exists(other)))))
}
