package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestProjectJoinJoinRemove(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectJoinJoinRemove")
	tBase, tBaseCols := e.scan("nn", 1)
	tOther, tOtherCols := e.scan("nn", 1)
	cScan, cCols := e.scan("nn", 1)
	bottom := e.f.ConstructLeftJoin(tBase, tOther, e.filters(e.f.ConstructEq(e.v(tBaseCols[0]), e.v(tOtherCols[0]))), memo.EmptyJoinPrivate)
	top := e.f.ConstructLeftJoin(bottom, cScan, e.filters(e.f.ConstructEq(e.v(tBaseCols[0]), e.v(cCols[0]))), memo.EmptyJoinPrivate)
	before := e.f.ConstructProject(top, memo.ProjectionsExpr{}, opt.MakeColSet(tBaseCols[0], cCols[0]))
	direct := e.f.ConstructLeftJoin(tBase, cScan, e.filters(e.f.ConstructEq(e.v(tBaseCols[0]), e.v(cCols[0]))), memo.EmptyJoinPrivate)
	want := e.f.ConstructProject(direct, memo.ProjectionsExpr{}, opt.MakeColSet(tBaseCols[0], cCols[0]))
	e.expectRewrite(rule, before, want)

	missLeft, missLeftCols := e.scan("nn", 1)
	missRight, _ := e.scan("nn", 1)
	missBottom := e.f.ConstructLeftJoin(missLeft, missRight, e.filters(e.f.ConstructEq(e.v(missLeftCols[0]), e.num(0))), memo.EmptyJoinPrivate)
	missTop := e.f.ConstructLeftJoin(missBottom, cScan, e.filters(e.f.ConstructEq(e.v(missLeftCols[0]), e.v(cCols[0]))), memo.EmptyJoinPrivate)
	nearMiss := e.f.ConstructProject(missTop, memo.ProjectionsExpr{}, opt.MakeColSet(missLeftCols[0], cCols[0]))
	e.expectNoMatch(rule, nearMiss)
}
