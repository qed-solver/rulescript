package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestProjectJoinRemove(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectJoinRemove")
	left, leftCols := e.scan("t", 1)
	right, rightCols := e.scan("t", 1)
	join := e.f.ConstructLeftJoin(left, right, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0]))), memo.EmptyJoinPrivate)
	before := e.f.ConstructProject(join, memo.EmptyProjectionsExpr, opt.MakeColSet(leftCols[0]))
	want := e.f.ConstructProject(left, memo.EmptyProjectionsExpr, opt.MakeColSet(leftCols[0]))
	e.expectRewrite(rule, before, want)

	missLeft, missLeftCols := e.scan("t", 1)
	missRight, _ := e.scan("t", 1)
	missJoin := e.f.ConstructLeftJoin(missLeft, missRight, e.filters(e.f.ConstructEq(e.v(missLeftCols[0]), e.num(1))), memo.EmptyJoinPrivate)
	nearMiss := e.f.ConstructProject(missJoin, memo.EmptyProjectionsExpr, opt.MakeColSet(missLeftCols[0]))
	e.expectNoMatch(rule, nearMiss)
}
