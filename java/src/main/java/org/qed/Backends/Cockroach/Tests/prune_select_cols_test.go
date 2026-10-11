package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneSelectCols(t *testing.T) {
	rule := loadRule(t, "PruneSelectCols")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	out := e.f.Metadata().AddColumn("sum", types.Int)
	filter := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(cols[0]), e.v(cols[1]))))
	before := e.f.ConstructProject(filter, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), out),
	}, opt.ColSet{})
	pruned := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0], cols[1]))
	wantFilter := e.f.ConstructSelect(pruned, e.filters(e.f.ConstructGt(e.v(cols[0]), e.v(cols[1]))))
	want := e.f.ConstructProject(wantFilter, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), out),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	filterOnExtra := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(cols[0]), e.v(cols[2]))))
	e.expectNoMatch(rule, e.f.ConstructProject(filterOnExtra, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), out),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(filter, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[2])), out),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, filter)
}

func TestSQLPruneSelectCols(t *testing.T) {
	expectSQLFiresOrNot(t, "PruneSelectCols", "SELECT a+b FROM (VALUES (1,2,3),(4,5,6)) AS v(a,b,c) WHERE a>b", true)
}
