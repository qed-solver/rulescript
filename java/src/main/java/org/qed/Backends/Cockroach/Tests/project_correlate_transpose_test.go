package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestProjectCorrelateTranspose(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "ProjectCorrelateTranspose")
	if _, err := e.cat.ExecuteDDL("CREATE TABLE l (a INT, b INT)"); err != nil {
		t.Fatal(err)
	}
	if _, err := e.cat.ExecuteDDL("CREATE TABLE r (a INT, b INT)"); err != nil {
		t.Fatal(err)
	}
	left, leftCols := e.scan("l", 2)
	right, rightCols := e.scan("r", 2)
	joined := e.f.ConstructInnerJoin(left, right, nil, &memo.JoinPrivate{})
	before := e.f.ConstructProject(joined, memo.EmptyProjectionsExpr, opt.MakeColSet(leftCols[0], rightCols[0]))

	leftProjection := e.f.ConstructProject(left, memo.EmptyProjectionsExpr, opt.MakeColSet(leftCols[0]))
	rightProjection := e.f.ConstructProject(right, memo.EmptyProjectionsExpr, opt.MakeColSet(rightCols[0]))
	transposedJoin := e.f.ConstructInnerJoin(leftProjection, rightProjection, nil, &memo.JoinPrivate{})
	want := e.f.ConstructProject(transposedJoin, memo.EmptyProjectionsExpr, opt.MakeColSet(leftCols[0], rightCols[0]))
	e.expectRewrite(rule, before, want)

	missLeft, missLeftCols := e.scan("l", 2)
	missRight, missRightCols := e.scan("r", 2)
	missJoin := e.f.ConstructInnerJoin(missLeft, missRight, nil, &memo.JoinPrivate{})
	nearMiss := e.f.ConstructProject(missJoin, memo.EmptyProjectionsExpr, opt.MakeColSet(missLeftCols[1], missRightCols[0]))
	e.expectNoMatch(rule, nearMiss)
}
