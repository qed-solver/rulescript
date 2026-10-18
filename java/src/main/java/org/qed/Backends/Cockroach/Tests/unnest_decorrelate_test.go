package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestUnnestDecorrelate(t *testing.T) {
	rule := loadRule(t, "UnnestDecorrelate")
	e := newEnv(t)

	left, leftCols := e.scan("t", 2)
	elements, elementCols := e.scan("u", 1)
	join := e.f.ConstructInnerJoin(left, elements, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(elementCols[0]))), &memo.JoinPrivate{})
	resultCol := e.f.Metadata().AddColumn("result", e.v(elementCols[0]).DataType())
	before := e.f.ConstructProject(join, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(elementCols[0]), resultCol)}, opt.ColSet{})

	leftProjection := e.f.ConstructProject(left, nil, opt.MakeColSet(leftCols[0]))
	expectedJoin := e.f.ConstructInnerJoin(leftProjection, elements, e.filters(e.f.ConstructEq(e.v(leftCols[0]), e.v(elementCols[0]))), &memo.JoinPrivate{})
	expected := e.f.ConstructProject(expectedJoin, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(elementCols[0]), resultCol)}, opt.ColSet{})
	e.expectRewrite(rule, before, expected)

	nearMiss := e.f.ConstructProject(join, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(leftCols[1]), resultCol)}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)

}
